package com.example.eventflow.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.AnalyticsDTO;
import com.example.eventflow.dto.EventDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.security.AccessPolicy;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.EventService;

import jakarta.validation.Valid;

/**
 * REST Controller for Event operations
 * FIX: Added proper error handling and response codes
 * FIX #1: Reordered routes - /search comes BEFORE /{id}
 * FIX #2: /analytics comes AFTER /search but BEFORE /{id}
 */
@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*", allowedHeaders = { "Content-Type", "Authorization" }, methods = { RequestMethod.GET,
        RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE }, allowCredentials = "false")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;
    private final AccessPolicy accessPolicy;

    public EventController(EventService eventService, AccessPolicy accessPolicy) {
        this.eventService = eventService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        log.info("GET /api/events - Fetch all events");
        List<EventDTO> events = eventService.getAllEvents();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/search")
    public ResponseEntity<List<EventDTO>> searchEvents(@RequestParam(required = false, defaultValue = "") String q) {
        log.info("GET /api/events/search?q={} - Search events", q);
        if (q == null || q.trim().isEmpty()) {
            log.debug("Empty search query, returning all events");
            return ResponseEntity.ok(eventService.getAllEvents());
        }
        List<EventDTO> events = eventService.searchEvents(q.trim());
        log.debug("Found {} events matching '{}'", events.size(), q);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDTO> getAnalytics() {
        log.info("GET /api/events/analytics - Fetch analytics");
        AnalyticsDTO analytics = eventService.getAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id) {
        log.info("GET /api/events/{} - Fetch event by ID", id);
        try {
            EventDTO event = eventService.getEventById(id);
            return ResponseEntity.ok(event);
        } catch (RuntimeException e) {
            log.error("Event not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@Valid @RequestBody EventDTO eventDTO,
            @AuthenticationPrincipal AppUserDetails me) {
        log.info("POST /api/events - Create new event: {}", eventDTO.getName());
        log.debug("Event DTO received: {}", eventDTO);
        try {
            EventDTO createdEvent = eventService.createEvent(eventDTO, me.getId());
            log.info("Event created successfully with ID: {}", createdEvent.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
        } catch (Exception e) {
            log.error("Error creating event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<EventDTO> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventDTO eventDTO,
            @AuthenticationPrincipal AppUserDetails me) {
        log.info("PUT /api/events/{} - Update event", id);
        log.debug("Event DTO received: {}", eventDTO);
        accessPolicy.requireManageable(id, me); // organizers may only edit their own events
        try {
            EventDTO updatedEvent = eventService.updateEvent(id, eventDTO);
            log.info("Event updated successfully: {}", id);
            return ResponseEntity.ok(updatedEvent);
        } catch (RuntimeException e) {
            log.error("Error updating event: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        log.info("DELETE /api/events/{} - Delete event", id);
        accessPolicy.requireManageable(id, me); // organizers may only delete their own events
        try {
            eventService.deleteEvent(id);
            log.info("Event deleted successfully: {}", id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting event: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /** Declared here because the generic RuntimeException handler below would otherwise turn these into 400s. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<String> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleException(RuntimeException ex) {
        log.error("Error occurred: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Validation failed");
        log.error("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }
}
