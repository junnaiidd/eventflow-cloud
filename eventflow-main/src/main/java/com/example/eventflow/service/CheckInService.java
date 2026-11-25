package com.example.eventflow.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.CheckInDTOs;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Registration;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.security.AccessPolicy;
import com.example.eventflow.security.AppUserDetails;

/** Validates scanned tickets and records attendance. All checks happen here, never in the browser. */
@Service
public class CheckInService {

    public static final String SUCCESS = "SUCCESS";
    public static final String INVALID_TICKET = "INVALID_TICKET";
    public static final String WRONG_EVENT = "WRONG_EVENT";
    public static final String CANCELLED = "CANCELLED";
    public static final String ALREADY_CHECKED_IN = "ALREADY_CHECKED_IN";
    public static final String EVENT_CLOSED = "EVENT_CLOSED";

    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

    private final RegistrationRepository registrations;
    private final AccessPolicy access;
    private final CertificateService certificates;

    public CheckInService(RegistrationRepository registrations, AccessPolicy access,
            CertificateService certificates) {
        this.registrations = registrations;
        this.access = access;
        this.certificates = certificates;
    }

    /**
     * Checks a ticket in for the given event. Failures are returned as results (not exceptions) so the
     * scanner can show a clear message; authorization problems still throw.
     */
    @Transactional
    public CheckInDTOs.Result checkIn(Long eventId, String rawTicketId, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);

        String ticketId = TicketService.normalize(rawTicketId);
        if (!TicketService.isWellFormed(ticketId)) {
            return failure(INVALID_TICKET, "This is not a valid EventFlow ticket", event, null);
        }
        if ("Cancelled".equals(event.getStatus())) {
            return failure(EVENT_CLOSED, "This event has been cancelled", event, null);
        }

        Registration reg = registrations.findByTicketId(ticketId).orElse(null);
        if (reg == null) {
            return failure(INVALID_TICKET, "Ticket not recognised", event, null);
        }
        if (!reg.getEvent().getId().equals(eventId)) {
            // Deliberately vague: do not reveal which event the ticket belongs to.
            return failure(WRONG_EVENT, "This ticket is for a different event", event, null);
        }
        if (reg.getStatus() != RegistrationStatus.CONFIRMED) {
            return failure(CANCELLED, "This registration was cancelled, the ticket is no longer valid", event, reg);
        }
        if (reg.isCheckedIn()) {
            return failure(ALREADY_CHECKED_IN, "This ticket was already used", event, reg);
        }

        // Atomic: the UPDATE only matches while the ticket is still unused and CONFIRMED, so two scanners
        // racing on the same ticket cannot both succeed.
        Instant now = Instant.now();
        int updated = registrations.markCheckedIn(reg.getId(), now, RegistrationStatus.CONFIRMED);
        Registration fresh = registrations.findById(reg.getId()).orElseThrow();
        if (updated == 0) {
            if (fresh.getStatus() != RegistrationStatus.CONFIRMED) {
                return failure(CANCELLED, "This registration was cancelled, the ticket is no longer valid", event, fresh);
            }
            return failure(ALREADY_CHECKED_IN, "This ticket was already used", event, fresh);
        }

        certificates.issueFor(fresh); // attendance earns the certificate
        log.info("Checked in ticket {} for event {}", ticketId, eventId);
        return new CheckInDTOs.Result(true, SUCCESS, "Check-in successful", fresh.getUser().getName(), event.getName(),
                event.getEventDate(), ticketId, fresh.getCheckedInAt(), checkedIn(eventId), confirmed(eventId));
    }

    @Transactional(readOnly = true)
    public CheckInDTOs.Stats stats(Long eventId, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);
        long confirmed = confirmed(eventId);
        long checkedIn = checkedIn(eventId);
        double percent = confirmed == 0 ? 0.0 : Math.round(checkedIn * 1000.0 / confirmed) / 10.0;
        return new CheckInDTOs.Stats(eventId, event.getName(), confirmed, checkedIn, Math.max(0, confirmed - checkedIn), percent);
    }

    private CheckInDTOs.Result failure(String code, String message, Event event, Registration reg) {
        Long eventId = event.getId();
        return new CheckInDTOs.Result(false, code, message,
                reg == null ? null : reg.getUser().getName(), event.getName(), event.getEventDate(),
                reg == null ? null : reg.getTicketId(), reg == null ? null : reg.getCheckedInAt(),
                checkedIn(eventId), confirmed(eventId));
    }

    private long confirmed(Long eventId) {
        return registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED);
    }

    private long checkedIn(Long eventId) {
        return registrations.countCheckedIn(eventId);
    }
}
