package com.example.eventflow.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.AnalyticsOverviewDTO;
import com.example.eventflow.dto.AnalyticsOverviewDTO.EventStat;
import com.example.eventflow.dto.AnalyticsOverviewDTO.TrendPoint;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.security.AppUserDetails;

/** Registration / attendance analytics computed from the database. Organizers see their events, admins all. */
@Service
public class AnalyticsService {

    private static final Set<String> OPEN_STATUSES = Set.of("Upcoming", "In Progress");

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final ZoneId zone;

    public AnalyticsService(EventRepository events, RegistrationRepository registrations,
            @Value("${spring.jackson.time-zone:Asia/Kolkata}") String timeZone) {
        this.events = events;
        this.registrations = registrations;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewDTO overview(AppUserDetails actor) {
        List<Event> scope = actor.isAdmin() ? events.findAll() : events.findByOrganizerId(actor.getId());
        if (scope.isEmpty()) {
            return new AnalyticsOverviewDTO(0, 0, 0, 0, 0, 0, 0.0, 0, List.of(), List.of(), List.of());
        }
        List<Long> ids = scope.stream().map(Event::getId).toList();

        // eventId -> [confirmed, waitlisted, cancelled, checkedIn]
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : registrations.summarizeByEvent(ids)) {
            long[] c = counts.computeIfAbsent((Long) row[0], k -> new long[4]);
            long count = ((Number) row[2]).longValue();
            long checkedIn = row[3] == null ? 0 : ((Number) row[3]).longValue();
            switch ((RegistrationStatus) row[1]) {
                case CONFIRMED -> {
                    c[0] += count;
                    c[3] += checkedIn;
                }
                case WAITLISTED -> c[1] += count;
                case CANCELLED -> c[2] += count;
            }
        }

        List<EventStat> stats = new ArrayList<>();
        long confirmed = 0, waitlisted = 0, cancelled = 0, attendees = 0, available = 0;
        for (Event e : scope) {
            long[] c = counts.getOrDefault(e.getId(), new long[4]);
            int capacity = e.getEffectiveCapacity();
            long free = Math.max(0, capacity - c[0]);
            stats.add(new EventStat(e.getId(), e.getName(), e.getEventDate(), e.getStatus(), capacity,
                    c[0], c[1], c[2], c[3], free, percent(c[3], c[0])));
            confirmed += c[0];
            waitlisted += c[1];
            cancelled += c[2];
            attendees += c[3];
            if (OPEN_STATUSES.contains(e.getStatus())) {
                available += free;
            }
        }
        stats.sort(Comparator.comparing(EventStat::eventDate, Comparator.nullsLast(Comparator.naturalOrder())));

        List<EventStat> popular = stats.stream()
                .filter(s -> s.confirmed() + s.waitlisted() > 0)
                .sorted(Comparator.comparingLong((EventStat s) -> s.confirmed() + s.waitlisted()).reversed()
                        .thenComparing(EventStat::name))
                .limit(5)
                .toList();

        // registrations per day (all statuses: the date someone signed up, whether or not they cancelled later)
        TreeMap<LocalDate, Long> perDay = new TreeMap<>();
        for (Instant at : registrations.findRegisteredAtByEventIds(ids)) {
            perDay.merge(at.atZone(zone).toLocalDate(), 1L, Long::sum);
        }
        List<TrendPoint> trend = perDay.entrySet().stream().map(en -> new TrendPoint(en.getKey(), en.getValue())).toList();

        return new AnalyticsOverviewDTO(scope.size(), confirmed + waitlisted, confirmed, waitlisted, cancelled,
                attendees, percent(attendees, confirmed), available, stats, popular, trend);
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 1000.0 / whole) / 10.0;
    }
}
