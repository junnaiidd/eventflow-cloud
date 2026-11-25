package com.example.eventflow.dto;

import java.time.LocalDate;
import java.util.List;

/** Registration and attendance analytics. Every number is computed from database records. */
public record AnalyticsOverviewDTO(
        long totalEvents,
        /** Confirmed + waitlisted (cancelled registrations are not counted). */
        long totalRegistrations,
        long confirmedRegistrations,
        long waitlistedRegistrations,
        long cancelledRegistrations,
        /** Confirmed participants who were checked in. */
        long totalAttendees,
        /** totalAttendees / confirmedRegistrations * 100, one decimal. */
        double attendancePercent,
        /** Free seats across events that are still open (Upcoming / In Progress). */
        long availableSeats,
        List<EventStat> events,
        List<EventStat> popularEvents,
        List<TrendPoint> registrationTrend) {

    public record EventStat(Long eventId, String name, LocalDate eventDate, String status, int capacity,
            long confirmed, long waitlisted, long cancelled, long checkedIn, long availableSeats,
            double attendancePercent) {
    }

    public record TrendPoint(LocalDate date, long registrations) {
    }
}
