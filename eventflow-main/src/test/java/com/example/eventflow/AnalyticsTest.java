package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.eventflow.dto.AnalyticsOverviewDTO;
import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.service.AnalyticsService;
import com.example.eventflow.service.CheckInService;
import com.example.eventflow.service.RegistrationService;

class AnalyticsTest extends TestSupport {

    @Autowired RegistrationService registrations;
    @Autowired CheckInService checkIn;
    @Autowired AnalyticsService analytics;

    @Test
    void countsAndAttendanceComeFromRealRecords() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event big = newEvent(org, 4);   // 3 registered, 2 attend
        Event small = newEvent(org, 1); // 1 confirmed + 1 waitlisted + 1 cancelled-waitlist
        AppUser p1 = newUser("P1", Role.PARTICIPANT), p2 = newUser("P2", Role.PARTICIPANT), p3 = newUser("P3", Role.PARTICIPANT);

        RegistrationDTO r1 = registrations.register(big.getId(), p1.getId());
        RegistrationDTO r2 = registrations.register(big.getId(), p2.getId());
        registrations.register(big.getId(), p3.getId());
        checkIn.checkIn(big.getId(), r1.ticketId(), asPrincipal(org));
        checkIn.checkIn(big.getId(), r2.ticketId(), asPrincipal(org));

        registrations.register(small.getId(), p1.getId());
        registrations.register(small.getId(), p2.getId());
        RegistrationDTO waiting = registrations.register(small.getId(), p3.getId());
        registrations.cancel(waiting.id(), asPrincipal(p3));

        AnalyticsOverviewDTO o = analytics.overview(asPrincipal(org));

        assertEquals(2, o.totalEvents());
        assertEquals(5, o.confirmedRegistrations() + o.waitlistedRegistrations(), "3 + 1 confirmed and 1 waitlisted");
        assertEquals(4, o.confirmedRegistrations());
        assertEquals(1, o.waitlistedRegistrations());
        assertEquals(1, o.cancelledRegistrations());
        assertEquals(o.confirmedRegistrations() + o.waitlistedRegistrations(), o.totalRegistrations());
        assertEquals(2, o.totalAttendees());
        assertEquals(50.0, o.attendancePercent(), 0.001, "2 of 4 confirmed attended");
        assertEquals(1, o.availableSeats(), "big has 4 seats, 3 taken; small is full");

        var bigStat = o.events().stream().filter(s -> s.eventId().equals(big.getId())).findFirst().orElseThrow();
        assertEquals(3, bigStat.confirmed());
        assertEquals(2, bigStat.checkedIn());
        assertEquals(1, bigStat.availableSeats());
        assertEquals(66.7, bigStat.attendancePercent(), 0.001);

        assertEquals(big.getId(), o.popularEvents().get(0).eventId(), "most registrations first");
        assertEquals(6, o.registrationTrend().stream().mapToLong(t -> t.registrations()).sum(), "every registration row has a date");
    }

    @Test
    void organizerOnlySeesOwnEventsAndEmptyScopeIsZero() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser other = newUser("Other Organizer", Role.ORGANIZER);
        newEvent(org, 5);

        assertEquals(1, analytics.overview(asPrincipal(org)).totalEvents());
        AnalyticsOverviewDTO empty = analytics.overview(asPrincipal(other));
        assertEquals(0, empty.totalEvents());
        assertEquals(0.0, empty.attendancePercent());
        assertTrue(empty.events().isEmpty());
    }
}
