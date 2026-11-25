package com.example.eventflow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.EventDTO;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.model.Role;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.service.EventService;

import java.time.LocalDate;

/** Administrator dashboard numbers and the event approval actions (admin only, see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record Stats(long students, long organizers, long admins, long totalEvents, long upcomingEvents,
            long pendingApprovals, long totalRegistrations, long confirmedRegistrations, long waitlistedRegistrations) {
    }

    private final AppUserRepository users;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final EventService eventService;

    public AdminController(AppUserRepository users, EventRepository events, RegistrationRepository registrations,
            EventService eventService) {
        this.users = users;
        this.events = events;
        this.registrations = registrations;
        this.eventService = eventService;
    }

    @GetMapping("/stats")
    public Stats stats() {
        long confirmed = registrations.countByStatus(RegistrationStatus.CONFIRMED);
        long waitlisted = registrations.countByStatus(RegistrationStatus.WAITLISTED);
        return new Stats(users.countByRole(Role.PARTICIPANT), users.countByRole(Role.ORGANIZER), users.countByRole(Role.ADMIN),
                events.count(), events.countByStatusAndEventDateGreaterThanEqual(EventService.PUBLISHED, LocalDate.now()),
                events.countByStatus(EventService.PENDING), confirmed + waitlisted, confirmed, waitlisted);
    }

    @PostMapping("/events/{id}/approve")
    public EventDTO approve(@PathVariable Long id) {
        return eventService.approve(id);
    }

    @PostMapping("/events/{id}/reject")
    public EventDTO reject(@PathVariable Long id) {
        return eventService.reject(id);
    }
}
