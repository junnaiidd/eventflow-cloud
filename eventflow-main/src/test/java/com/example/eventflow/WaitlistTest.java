package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.model.Role;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.service.RegistrationService;

class WaitlistTest extends TestSupport {

    @Autowired RegistrationService registrations;
    @Autowired RegistrationRepository registrationRepository;

    @Test
    void fullEventPutsParticipantsOnWaitlistWithCorrectPositions() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 2);
        AppUser a = newUser("A", Role.PARTICIPANT), b = newUser("B", Role.PARTICIPANT);
        AppUser c = newUser("C", Role.PARTICIPANT), d = newUser("D", Role.PARTICIPANT);

        assertEquals(RegistrationStatus.CONFIRMED, registrations.register(e.getId(), a.getId()).status());
        assertEquals(RegistrationStatus.CONFIRMED, registrations.register(e.getId(), b.getId()).status());
        RegistrationDTO rc = registrations.register(e.getId(), c.getId());
        RegistrationDTO rd = registrations.register(e.getId(), d.getId());

        assertEquals(RegistrationStatus.WAITLISTED, rc.status());
        assertEquals(1, rc.waitlistPosition());
        assertNull(rc.ticketId(), "waitlisted people have no ticket");
        assertEquals(2, rd.waitlistPosition());
        assertEquals(2, registrationRepository.countByEventIdAndStatus(e.getId(), RegistrationStatus.CONFIRMED));
    }

    @Test
    void cancellationPromotesTheFirstWaitlistedPerson() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 1);
        AppUser a = newUser("A", Role.PARTICIPANT), b = newUser("B", Role.PARTICIPANT), c = newUser("C", Role.PARTICIPANT);
        RegistrationDTO ra = registrations.register(e.getId(), a.getId());
        RegistrationDTO rb = registrations.register(e.getId(), b.getId());
        registrations.register(e.getId(), c.getId());

        registrations.cancel(ra.id(), asPrincipal(a));

        RegistrationDTO promoted = registrations.get(rb.id(), asPrincipal(b));
        assertEquals(RegistrationStatus.CONFIRMED, promoted.status());
        assertNotNull(promoted.ticketId(), "promoted participant receives a ticket");
        RegistrationDTO stillWaiting = registrations.listForUser(c.getId()).get(0);
        assertEquals(RegistrationStatus.WAITLISTED, stillWaiting.status());
        assertEquals(1, stillWaiting.waitlistPosition(), "queue moves up");
        assertEquals(1, registrationRepository.countByEventIdAndStatus(e.getId(), RegistrationStatus.CONFIRMED));
    }

    @Test
    void leavingTheWaitlistDoesNotPromoteAnyone() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 1);
        AppUser a = newUser("A", Role.PARTICIPANT), b = newUser("B", Role.PARTICIPANT), c = newUser("C", Role.PARTICIPANT);
        registrations.register(e.getId(), a.getId());
        RegistrationDTO rb = registrations.register(e.getId(), b.getId());
        RegistrationDTO rc = registrations.register(e.getId(), c.getId());

        registrations.cancel(rb.id(), asPrincipal(b));

        assertEquals(1, registrations.get(rc.id(), asPrincipal(c)).waitlistPosition());
        assertEquals(1, registrationRepository.countByEventIdAndStatus(e.getId(), RegistrationStatus.CONFIRMED));
    }

    @Test
    void duplicateRegistrationAndDuplicateWaitlistEntryAreRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 1);
        AppUser a = newUser("A", Role.PARTICIPANT), b = newUser("B", Role.PARTICIPANT);
        registrations.register(e.getId(), a.getId());
        registrations.register(e.getId(), b.getId());

        ApiException dupConfirmed = assertThrows(ApiException.class, () -> registrations.register(e.getId(), a.getId()));
        ApiException dupWaitlist = assertThrows(ApiException.class, () -> registrations.register(e.getId(), b.getId()));

        assertEquals(409, dupConfirmed.getStatus().value());
        assertEquals(409, dupWaitlist.getStatus().value());
        assertEquals(2, registrationRepository.findByEventIdOrderByRegisteredAtAsc(e.getId()).size());
    }

    @Test
    void cancelledParticipantCanRegisterAgainWithANewTicket() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 3);
        AppUser a = newUser("A", Role.PARTICIPANT);
        RegistrationDTO first = registrations.register(e.getId(), a.getId());
        registrations.cancel(first.id(), asPrincipal(a));

        RegistrationDTO second = registrations.register(e.getId(), a.getId());

        assertEquals(first.id(), second.id(), "one row per event and user");
        assertEquals(RegistrationStatus.CONFIRMED, second.status());
        assertTrue(!first.ticketId().equals(second.ticketId()));
    }

    @Test
    void raisingCapacityPromotesWaitingParticipants() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 1);
        AppUser a = newUser("A", Role.PARTICIPANT), b = newUser("B", Role.PARTICIPANT);
        registrations.register(e.getId(), a.getId());
        RegistrationDTO rb = registrations.register(e.getId(), b.getId());

        e.setCapacity(2);
        events.save(e);
        int promoted = registrations.fillAvailableSeats(e.getId());

        assertEquals(1, promoted);
        assertEquals(RegistrationStatus.CONFIRMED, registrations.get(rb.id(), asPrincipal(b)).status());
    }

    @Test
    void closedEventRejectsRegistration() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 3);
        e.setStatus("Completed");
        events.save(e);
        AppUser a = newUser("A", Role.PARTICIPANT);

        assertThrows(ApiException.class, () -> registrations.register(e.getId(), a.getId()));
    }

    @Test
    void simultaneousRegistrationsNeverExceedCapacity() throws Exception {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        int capacity = 3, people = 10;
        Event e = newEvent(org, capacity);
        List<AppUser> participants = new ArrayList<>();
        for (int i = 0; i < people; i++) participants.add(newUser("Racer " + i, Role.PARTICIPANT));

        ExecutorService pool = Executors.newFixedThreadPool(people);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RegistrationDTO>> futures = new ArrayList<>();
        for (AppUser p : participants) {
            futures.add(pool.submit(() -> {
                start.await();
                return registrations.register(e.getId(), p.getId());
            }));
        }
        start.countDown();
        int confirmed = 0;
        Set<Integer> positions = new HashSet<>();
        for (Future<RegistrationDTO> f : futures) {
            RegistrationDTO r = f.get(30, TimeUnit.SECONDS);
            if (r.status() == RegistrationStatus.CONFIRMED) confirmed++;
        }
        pool.shutdown();

        assertEquals(capacity, confirmed);
        assertEquals(capacity, registrationRepository.countByEventIdAndStatus(e.getId(), RegistrationStatus.CONFIRMED));
        assertEquals(people - capacity, registrationRepository.countByEventIdAndStatus(e.getId(), RegistrationStatus.WAITLISTED));
        for (RegistrationDTO r : registrations.listForEvent(e.getId(), asPrincipal(org))) {
            if (r.status() == RegistrationStatus.WAITLISTED) {
                assertTrue(positions.add(r.waitlistPosition()), "waitlist positions must be unique");
            }
        }
    }
}
