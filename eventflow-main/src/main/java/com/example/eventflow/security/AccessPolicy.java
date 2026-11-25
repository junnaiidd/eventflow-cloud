package com.example.eventflow.security;

import org.springframework.stereotype.Component;

import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.Event;
import com.example.eventflow.repository.EventRepository;

/**
 * Ownership rules for organizer features: admins manage every event, organizers only the events they
 * created. Events without an owner (legacy data) are admin-only.
 */
@Component
public class AccessPolicy {

    private final EventRepository events;

    public AccessPolicy(EventRepository events) {
        this.events = events;
    }

    public boolean canManage(Event event, AppUserDetails actor) {
        if (actor == null) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        return actor.isOrganizerOrAdmin() && event.getOrganizer() != null
                && event.getOrganizer().getId().equals(actor.getId());
    }

    public void assertCanManage(Event event, AppUserDetails actor) {
        if (!canManage(event, actor)) {
            throw ApiException.forbidden("You can only manage events that you created");
        }
    }

    /** Loads the event and checks ownership; 404 if it does not exist. */
    public Event requireManageable(Long eventId, AppUserDetails actor) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        assertCanManage(event, actor);
        return event;
    }
}
