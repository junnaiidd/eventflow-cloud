package com.example.eventflow.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Certificate;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Registration;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.CertificateRepository;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.security.AccessPolicy;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.pdf.TicketPdfGenerator;

/**
 * Registration, waitlist and ticket rules.
 *
 * <p>Every method that changes seat counts first locks the event row ({@code findByIdForUpdate}), so two
 * people registering or cancelling at the same moment are handled one after the other. That is what keeps
 * confirmed registrations at or below capacity and the waitlist order consistent.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final Set<String> OPEN_STATUSES = Set.of("Upcoming", "In Progress");

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final AppUserRepository users;
    private final CertificateRepository certificates;
    private final TicketService tickets;
    private final TicketPdfGenerator ticketPdf;
    private final AccessPolicy access;

    public RegistrationService(EventRepository events, RegistrationRepository registrations, AppUserRepository users,
            CertificateRepository certificates, TicketService tickets, TicketPdfGenerator ticketPdf,
            AccessPolicy access) {
        this.events = events;
        this.registrations = registrations;
        this.users = users;
        this.certificates = certificates;
        this.tickets = tickets;
        this.ticketPdf = ticketPdf;
        this.access = access;
    }

    // ------------------------------------------------------------------ register / cancel

    /** Registers the user: CONFIRMED with a ticket while seats are free, otherwise WAITLISTED. */
    @Transactional
    public RegistrationDTO register(Long eventId, Long userId) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!isOpen(event)) {
            throw ApiException.conflict("Registration is closed for this event");
        }
        int capacity = event.getEffectiveCapacity();
        if (capacity <= 0) {
            throw ApiException.conflict("Registration is not open yet: this event has no capacity set");
        }
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));

        Registration reg = registrations.findByEventIdAndUserId(eventId, userId).orElse(null);
        if (reg != null) {
            if (reg.getStatus() == RegistrationStatus.CONFIRMED) {
                throw ApiException.conflict("You are already registered for this event");
            }
            if (reg.getStatus() == RegistrationStatus.WAITLISTED) {
                throw ApiException.conflict("You are already on the waitlist (position " + waitlistPosition(reg) + ")");
            }
            // CANCELLED: re-use the row (one row per event+user) and start over
        } else {
            reg = new Registration();
            reg.setEvent(event);
            reg.setUser(user);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        reg.setRegisteredAt(now);
        reg.setQueuedAt(now);
        reg.setCancelledAt(null);
        reg.setCheckedInAt(null);
        reg.setTicketId(null);

        if (registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED) < capacity) {
            confirm(reg);
        } else {
            reg.setStatus(RegistrationStatus.WAITLISTED);
        }
        Registration saved = registrations.saveAndFlush(reg);
        log.info("User {} registered for event {} -> {}", userId, eventId, saved.getStatus());
        return toDTO(saved);
    }

    /** Cancels the caller's own registration; a freed seat goes to the first person on the waitlist. */
    @Transactional
    public RegistrationDTO cancel(Long registrationId, AppUserDetails actor) {
        Long eventId = registrations.findEventIdById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        Registration reg = registrations.findById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found"));

        // Only the owner may cancel; other users get the same "not found" so IDs cannot be probed.
        if (!reg.getUser().getId().equals(actor.getId())) {
            throw ApiException.notFound("Registration not found");
        }
        if (reg.getStatus() == RegistrationStatus.CANCELLED) {
            throw ApiException.conflict("This registration is already cancelled");
        }
        if (reg.isCheckedIn()) {
            throw ApiException.conflict("You have already checked in and can no longer cancel");
        }

        boolean heldSeat = reg.getStatus() == RegistrationStatus.CONFIRMED;
        reg.setStatus(RegistrationStatus.CANCELLED); // ticketId is kept so a scan reports "cancelled", never "valid"
        reg.setCancelledAt(Instant.now());
        registrations.saveAndFlush(reg);

        if (heldSeat) {
            promoteFromWaitlist(event);
        }
        return toDTO(reg);
    }

    /** Call after an organizer raises an event's capacity: fills the new seats from the waitlist. */
    @Transactional
    public int fillAvailableSeats(Long eventId) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        return promoteFromWaitlist(event);
    }

    /** Promotes waitlisted people, oldest first, until the event is full or the queue is empty. */
    private int promoteFromWaitlist(Event event) {
        if (!isOpen(event)) {
            return 0;
        }
        int capacity = event.getEffectiveCapacity();
        int promoted = 0;
        while (registrations.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED) < capacity) {
            Optional<Registration> next = registrations
                    .findFirstByEventIdAndStatusOrderByQueuedAtAscIdAsc(event.getId(), RegistrationStatus.WAITLISTED);
            if (next.isEmpty()) {
                break;
            }
            Registration reg = next.get();
            confirm(reg);
            registrations.saveAndFlush(reg);
            promoted++;
            log.info("Promoted registration {} from the waitlist of event {}", reg.getId(), event.getId());
        }
        return promoted;
    }

    private void confirm(Registration reg) {
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setTicketId(tickets.generateUniqueTicketId());
    }

    private static boolean isOpen(Event event) {
        return OPEN_STATUSES.contains(event.getStatus());
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<RegistrationDTO> listForUser(Long userId) {
        return toDTOs(registrations.findByUserIdOrderByRegisteredAtDesc(userId));
    }

    @Transactional(readOnly = true)
    public List<RegistrationDTO> listForEvent(Long eventId, AppUserDetails actor) {
        access.requireManageable(eventId, actor);
        return toDTOs(registrations.findByEventIdOrderByRegisteredAtAsc(eventId));
    }

    @Transactional(readOnly = true)
    public RegistrationDTO get(Long registrationId, AppUserDetails actor) {
        return toDTO(requireViewable(registrationId, actor));
    }

    /** PNG QR code for a CONFIRMED ticket. Visible to its owner and to the event's organizer/admin. */
    @Transactional(readOnly = true)
    public byte[] ticketQr(Long registrationId, AppUserDetails actor) {
        Registration reg = requireTicket(registrationId, actor);
        return tickets.qrPng(reg.getTicketId(), 320);
    }

    @Transactional(readOnly = true)
    public byte[] ticketPdf(Long registrationId, AppUserDetails actor) {
        Registration reg = requireTicket(registrationId, actor);
        Event event = reg.getEvent();
        return ticketPdf.generate(new TicketPdfGenerator.TicketData(
                reg.getUser().getName(), event.getName(), event.getEventDate(), event.getEventTime(),
                event.getLocation(), reg.getTicketId(), reg.isCheckedIn() ? "Checked in" : "Confirmed",
                tickets.qrPng(reg.getTicketId(), 400)));
    }

    private Registration requireTicket(Long registrationId, AppUserDetails actor) {
        Registration reg = requireViewable(registrationId, actor);
        if (reg.getStatus() != RegistrationStatus.CONFIRMED || reg.getTicketId() == null) {
            throw ApiException.conflict("There is no valid ticket for this registration");
        }
        return reg;
    }

    private Registration requireViewable(Long registrationId, AppUserDetails actor) {
        Registration reg = registrations.findById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        boolean owner = reg.getUser().getId().equals(actor.getId());
        if (!owner && !access.canManage(reg.getEvent(), actor)) {
            throw ApiException.notFound("Registration not found");
        }
        return reg;
    }

    // ------------------------------------------------------------------ mapping

    private long waitlistPosition(Registration reg) {
        Instant queuedAt = reg.getQueuedAt() != null ? reg.getQueuedAt().truncatedTo(ChronoUnit.MICROS) : null;
        return registrations.countAheadInQueue(reg.getEvent().getId(), RegistrationStatus.WAITLISTED,
                queuedAt, reg.getId()) + 1;
    }

    private RegistrationDTO toDTO(Registration reg) {
        return toDTOs(List.of(reg)).get(0);
    }

    private List<RegistrationDTO> toDTOs(Collection<Registration> regs) {
        List<Long> ids = regs.stream().map(Registration::getId).toList();
        Map<Long, Certificate> certByReg = ids.isEmpty() ? Map.of()
                : certificates.findByRegistrationIdIn(ids).stream()
                        .collect(Collectors.toMap(c -> c.getRegistration().getId(), Function.identity()));
        return regs.stream().map(r -> {
            Event e = r.getEvent();
            AppUser u = r.getUser();
            Certificate cert = certByReg.get(r.getId());
            Integer position = r.getStatus() == RegistrationStatus.WAITLISTED ? (int) waitlistPosition(r) : null;
            return new RegistrationDTO(r.getId(), e.getId(), e.getName(), e.getEventDate(), e.getEventTime(),
                    e.getLocation(), e.getStatus(), u.getId(), u.getName(), u.getEmail(), r.getStatus(), position,
                    r.getStatus() == RegistrationStatus.CONFIRMED ? r.getTicketId() : null,
                    r.getRegisteredAt(), r.getCancelledAt(), r.getCheckedInAt(), r.isCheckedIn(),
                    cert == null ? null : cert.getId(), cert == null ? null : cert.getCertificateCode());
        }).toList();
    }
}
