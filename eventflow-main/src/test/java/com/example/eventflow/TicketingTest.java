package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.eventflow.dto.CheckInDTOs;
import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.model.Role;
import com.example.eventflow.service.CheckInService;
import com.example.eventflow.service.RegistrationService;
import com.example.eventflow.service.TicketService;

class TicketingTest extends TestSupport {

    @Autowired RegistrationService registrations;
    @Autowired CheckInService checkIn;
    @Autowired TicketService tickets;

    @Test
    void ticketIsGeneratedAfterRegistration() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);

        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        assertEquals(RegistrationStatus.CONFIRMED, r.status());
        assertNotNull(r.ticketId());
        assertTrue(TicketService.isWellFormed(r.ticketId()));
        assertTrue(registrations.ticketQr(r.id(), asPrincipal(p)).length > 100, "QR PNG should be produced");
        assertTrue(new String(registrations.ticketPdf(r.id(), asPrincipal(p)), 0, 4).startsWith("%PDF"));
    }

    @Test
    void ticketsAreUnique() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 20);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            AppUser p = newUser("Person " + i, Role.PARTICIPANT);
            assertTrue(ids.add(registrations.register(e.getId(), p.getId()).ticketId()), "duplicate ticket id");
        }
        assertEquals(15, ids.size());
    }

    @Test
    void validTicketChecksInAndIsRecorded() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        CheckInDTOs.Result result = checkIn.checkIn(e.getId(), r.ticketId().toLowerCase() + " ", asPrincipal(org));

        assertTrue(result.success());
        assertEquals(CheckInService.SUCCESS, result.code());
        assertEquals("Pat Participant", result.participantName());
        assertEquals(1, result.checkedInCount());
        assertTrue(registrations.get(r.id(), asPrincipal(p)).checkedIn(), "check-in must be stored in the database");
    }

    @Test
    void invalidTicketsAreRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 5);

        assertEquals(CheckInService.INVALID_TICKET, checkIn.checkIn(e.getId(), "not-a-ticket", asPrincipal(org)).code());
        assertEquals(CheckInService.INVALID_TICKET, checkIn.checkIn(e.getId(), "'; DROP TABLE registrations;--", asPrincipal(org)).code());
        assertEquals(CheckInService.INVALID_TICKET, checkIn.checkIn(e.getId(), "EF-AAAA-BBBB-CCCC", asPrincipal(org)).code());
        assertFalse(checkIn.checkIn(e.getId(), null, asPrincipal(org)).success());
    }

    @Test
    void duplicateCheckInIsRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        assertTrue(checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org)).success());
        CheckInDTOs.Result second = checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));

        assertFalse(second.success());
        assertEquals(CheckInService.ALREADY_CHECKED_IN, second.code());
        assertEquals(1, second.checkedInCount(), "attendance must not be counted twice");
    }

    @Test
    void cancelledRegistrationIsRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());
        registrations.cancel(r.id(), asPrincipal(p));

        CheckInDTOs.Result result = checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));

        assertFalse(result.success());
        assertEquals(CheckInService.CANCELLED, result.code());
    }

    @Test
    void ticketForAnotherEventIsRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event a = newEvent(org, 5);
        Event b = newEvent(org, 5);
        RegistrationDTO r = registrations.register(a.getId(), p.getId());

        CheckInDTOs.Result result = checkIn.checkIn(b.getId(), r.ticketId(), asPrincipal(org));

        assertEquals(CheckInService.WRONG_EVENT, result.code());
        assertFalse(registrations.get(r.id(), asPrincipal(p)).checkedIn());
    }

    @Test
    void organizerCannotCheckInForSomeoneElsesEvent() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser other = newUser("Other Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        ApiException ex = assertThrows(ApiException.class, () -> checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(other)));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void participantCannotSeeOrCancelAnotherUsersRegistration() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        AppUser intruder = newUser("Ivy Intruder", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        assertThrows(ApiException.class, () -> registrations.get(r.id(), asPrincipal(intruder)));
        assertThrows(ApiException.class, () -> registrations.ticketQr(r.id(), asPrincipal(intruder)));
        assertThrows(ApiException.class, () -> registrations.cancel(r.id(), asPrincipal(intruder)));
        assertNull(registrations.listForUser(intruder.getId()).stream().findAny().orElse(null));
    }
}
