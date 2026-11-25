package com.example.eventflow.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.AnalyticsDTO;
import com.example.eventflow.dto.EventDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.Role;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.dto.ParticipantDTO;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Participant;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.CertificateRepository;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.storage.FileStorageService;

/**
 * Service layer for Event business logic
 * Handles all event-related operations and analytics
 */
@Service
@Transactional
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final AppUserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final CertificateRepository certificateRepository;
    private final RegistrationService registrationService;
    private final FileStorageService storage;
    private final CollegeCatalog catalog;

    public static final String PENDING = "Pending Approval";
    public static final String REJECTED = "Rejected";
    public static final String PUBLISHED = "Upcoming";

    public EventService(EventRepository eventRepository, AppUserRepository userRepository,
            RegistrationRepository registrationRepository, CertificateRepository certificateRepository,
            RegistrationService registrationService, FileStorageService storage, CollegeCatalog catalog) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.certificateRepository = certificateRepository;
        this.registrationService = registrationService;
        this.storage = storage;
        this.catalog = catalog;
    }

    /** Students only see approved events; organizers also see their own pending/rejected ones; admins see all. */
    public static boolean isVisibleTo(EventDTO e, AppUserDetails actor) {
        boolean unapproved = PENDING.equals(e.getStatus()) || REJECTED.equals(e.getStatus());
        if (!unapproved) {
            return true;
        }
        if (actor == null) {
            return false;
        }
        return actor.isAdmin() || (actor.getRole() == Role.ORGANIZER && actor.getId().equals(e.getOrganizerId()));
    }

    public List<EventDTO> getAllEvents() {
        log.debug("Fetching all events");
        return eventRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
    public EventDTO getEventById(@NonNull Long id) {
        log.debug("Fetching event with ID: {}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));
        return convertToDTO(event);
    }

    public EventDTO createEvent(@NonNull EventDTO eventDTO) {
        return createEvent(eventDTO, null);
    }

    /**
     * Creates an event owned by the actor. Events created by an organizer start as "Pending Approval" and
     * need an administrator's approval before students can see or register for them; admin-created
     * events are published directly.
     */
    @SuppressWarnings("null")
    public EventDTO createEvent(@NonNull EventDTO eventDTO, AppUserDetails actor) {
        log.info("Creating new event: {}", eventDTO.getName());
        validateCollegeFields(eventDTO, null);

        Event event = convertToEntity(eventDTO);
        if (actor != null) {
            AppUser organizer = userRepository.findById(actor.getId()).orElse(null);
            event.setOrganizer(organizer);
            if (actor.getRole() == Role.ORGANIZER) {
                event.setStatus(PENDING);
            }
        }

        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDate.now());
        }
        if (event.getUpdatedAt() == null) {
            event.setUpdatedAt(LocalDate.now());
        }

        if (event.getParticipants() != null) {
            for (Participant participant : event.getParticipants()) {
                participant.setEvent(event);
            }
        }

        Event savedEvent = eventRepository.save(event);
        log.info("Event created with ID: {}", savedEvent.getId());

        return convertToDTO(savedEvent);
    }

    public EventDTO updateEvent(@NonNull Long id, @NonNull EventDTO eventDTO) {
        return updateEvent(id, eventDTO, null);
    }

    public EventDTO updateEvent(@NonNull Long id, @NonNull EventDTO eventDTO, AppUserDetails actor) {
        log.info("Updating event with ID: {}", id);

        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));

        validateCollegeFields(eventDTO, existingEvent);
        String previousStatus = existingEvent.getStatus();
        updateEventFields(existingEvent, eventDTO);
        if (actor != null && actor.getRole() == Role.ORGANIZER) {
            // Organizers cannot approve their own events: an unapproved event stays (or goes back to) pending.
            if (PENDING.equals(previousStatus) || REJECTED.equals(previousStatus) || PENDING.equals(eventDTO.getStatus())) {
                existingEvent.setStatus(PENDING);
            } else if (REJECTED.equals(eventDTO.getStatus())) {
                existingEvent.setStatus(previousStatus);
            }
        }
        existingEvent.setUpdatedAt(LocalDate.now());

        if (eventDTO.getParticipants() != null) {
            existingEvent.getParticipants().clear();

            for (ParticipantDTO pDto : eventDTO.getParticipants()) {
                Participant participant = Participant.builder()
                        .name(pDto.getName())
                        .role(pDto.getRole())
                        .event(existingEvent)
                        .build();
                existingEvent.getParticipants().add(participant);
            }
        }

        Event updatedEvent = eventRepository.save(existingEvent);
        log.info("Event updated successfully: {}", id);

        // A higher capacity (or re-opened event) may free seats for people waiting in the queue.
        registrationService.fillAvailableSeats(id);

        return convertToDTO(updatedEvent);
    }

    /** Admin approval: a pending event becomes published (status "Upcoming"). */
    public EventDTO approve(Long id) {
        return decide(id, PUBLISHED);
    }

    /** Admin rejection: the organizer can edit the event, which sends it back for approval. */
    public EventDTO reject(Long id) {
        return decide(id, REJECTED);
    }

    private EventDTO decide(Long id, String newStatus) {
        Event event = eventRepository.findById(id).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!PENDING.equals(event.getStatus())) {
            throw ApiException.conflict("Only events that are pending approval can be approved or rejected");
        }
        event.setStatus(newStatus);
        return convertToDTO(eventRepository.save(event));
    }

    /** Server-side checks for the college fields. Legacy values already stored on the event stay valid. */
    private void validateCollegeFields(EventDTO dto, Event existing) {
        String category = dto.getType();
        boolean unchangedCategory = existing != null && category != null && category.equals(existing.getType());
        if (!unchangedCategory && !catalog.isCategory(category)) {
            throw ApiException.badRequest("Choose an event category from the list");
        }
        String dept = dto.getDepartment();
        if (dept != null && !dept.isBlank()) {
            boolean unchangedDept = existing != null && dept.equals(existing.getDepartment());
            if (!unchangedDept && !catalog.isDepartment(dept)) {
                throw ApiException.badRequest("Choose a department from the list");
            }
        }
        if (dto.getEventTime() != null && dto.getEndTime() != null && !dto.getEndTime().isAfter(dto.getEventTime())) {
            throw ApiException.badRequest("End time must be after the start time");
        }
        if (dto.getRegistrationDeadline() != null && dto.getEventDate() != null
                && dto.getRegistrationDeadline().isAfter(dto.getEventDate())) {
            throw ApiException.badRequest("The registration deadline cannot be after the event date");
        }
    }

    public void deleteEvent(@NonNull Long id) {
        log.info("Deleting event with ID: {}", id);

        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Event not found with ID: " + id);
        }

        // Registrations and certificates reference the event, so they are removed first (children before parents).
        List<String> storedKeys = new ArrayList<>(certificateRepository.findStorageKeysByEventId(id));
        eventRepository.findById(id).map(Event::getPosterKey).ifPresent(storedKeys::add);
        certificateRepository.deleteByEventId(id);
        registrationRepository.deleteByEventId(id);
        eventRepository.deleteById(id);
        eventRepository.flush();
        for (String key : storedKeys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not delete stored file {}: {}", key, e.getMessage());
            }
        }
        log.info("Event deleted successfully: {}", id);
    }

    public List<EventDTO> searchEvents(String query) {
        log.debug("Searching events with query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            return getAllEvents();
        }

        String searchQuery = query.trim();

        List<Event> events = eventRepository.findByNameContainingIgnoreCase(searchQuery);

        if (events.isEmpty()) {
            events = eventRepository.findAll().stream()
                    .filter(event -> (event.getLocation() != null
                            && event.getLocation().toLowerCase().contains(searchQuery.toLowerCase())) ||
                            (event.getType() != null
                                    && event.getType().toLowerCase().contains(searchQuery.toLowerCase()))
                            ||
                            (event.getDescription() != null
                                    && event.getDescription().toLowerCase().contains(searchQuery.toLowerCase())))
                    .collect(Collectors.toList());
        }

        log.debug("Found {} events matching '{}'", events.size(), query);

        return events.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public AnalyticsDTO getAnalytics() {
        log.debug("Calculating analytics");

        List<Event> allEvents = eventRepository.findAll();

        if (allEvents.isEmpty()) {
            return createEmptyAnalytics();
        }

        Double totalBudget = eventRepository.getTotalBudget();
        Integer totalAttendees = eventRepository.getTotalAttendees();

        double safeTotalBudget = Objects.requireNonNullElse(totalBudget, 0.0);
        int safeTotalAttendees = Objects.requireNonNullElse(totalAttendees, 0);

        Long upcomingEvents = eventRepository.countByStatus("Upcoming");
        Long completedEvents = eventRepository.countByStatus("Completed");
        Long inProgressEvents = eventRepository.countByStatus("In Progress");
        Long cancelledEvents = eventRepository.countByStatus("Cancelled");

        long safeUpcomingEvents = Objects.requireNonNullElse(upcomingEvents, 0L);
        long safeCompletedEvents = Objects.requireNonNullElse(completedEvents, 0L);
        long safeInProgressEvents = Objects.requireNonNullElse(inProgressEvents, 0L);
        long safeCancelledEvents = Objects.requireNonNullElse(cancelledEvents, 0L);

        long totalNonCancelled = safeUpcomingEvents + safeCompletedEvents + safeInProgressEvents;
        Double successRate = totalNonCancelled > 0
                ? (safeCompletedEvents * 100.0) / totalNonCancelled
                : 0.0;

        Map<String, Long> eventsByType = allEvents.stream()
                .collect(Collectors.groupingBy(Event::getType, Collectors.counting()));

        Map<String, Double> budgetByType = allEvents.stream()
                .collect(Collectors.groupingBy(
                        Event::getType,
                        Collectors.summingDouble(event -> Objects.requireNonNullElse(event.getBudget(), 0.0))));

        Map<String, Long> eventsByMonth = allEvents.stream()
                .filter(event -> event.getEventDate() != null)
                .collect(Collectors.groupingBy(
                        event -> event.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));

        Map<String, Double> budgetByMonth = allEvents.stream()
                .filter(event -> event.getEventDate() != null)
                .collect(Collectors.groupingBy(
                        event -> event.getEventDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.summingDouble(event -> Objects.requireNonNullElse(event.getBudget(), 0.0))));

        return AnalyticsDTO.builder()
                .totalBudget(safeTotalBudget)
                .totalAttendees(safeTotalAttendees)
                .totalEvents((long) allEvents.size())
                .upcomingEvents(safeUpcomingEvents)
                .completedEvents(safeCompletedEvents)
                .inProgressEvents(safeInProgressEvents)
                .cancelledEvents(safeCancelledEvents)
                .averageBudget(!allEvents.isEmpty() ? safeTotalBudget / allEvents.size() : 0.0)
                .averageAttendees(!allEvents.isEmpty() ? (double) safeTotalAttendees / allEvents.size() : 0.0)
                .eventsByType(eventsByType)
                .budgetByType(budgetByType)
                .eventsByMonth(eventsByMonth)
                .budgetByMonth(budgetByMonth)
                .successRate(successRate)
                .build();
    }

    private AnalyticsDTO createEmptyAnalytics() {
        return AnalyticsDTO.builder()
                .totalBudget(0.0)
                .totalAttendees(0)
                .totalEvents(0L)
                .upcomingEvents(0L)
                .completedEvents(0L)
                .inProgressEvents(0L)
                .cancelledEvents(0L)
                .averageBudget(0.0)
                .averageAttendees(0.0)
                .eventsByType(new HashMap<>())
                .budgetByType(new HashMap<>())
                .eventsByMonth(new HashMap<>())
                .budgetByMonth(new HashMap<>())
                .successRate(0.0)
                .build();
    }

    private EventDTO convertToDTO(Event event) {
        if (event == null) {
            return null;
        }

        List<ParticipantDTO> participantDTOs = new ArrayList<>();
        if (event.getParticipants() != null) {
            participantDTOs = event.getParticipants().stream()
                    .map(this::convertParticipantToDTO)
                    .collect(Collectors.toList());
        }

        int effectiveCapacity = event.getEffectiveCapacity();
        int confirmed = (int) registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);
        int waitlisted = (int) registrationRepository.countByEventIdAndStatus(event.getId(), RegistrationStatus.WAITLISTED);
        AppUser organizer = event.getOrganizer();
        String posterUrl = event.getPosterKey() == null ? null
                : "/api/events/" + event.getId() + "/poster?v=" + Integer.toHexString(event.getPosterKey().hashCode());

        return EventDTO.builder()
                .capacity(effectiveCapacity)
                .department(event.getDepartment())
                .endTime(event.getEndTime())
                .registrationDeadline(event.getRegistrationDeadline())
                .confirmedCount(confirmed)
                .waitlistedCount(waitlisted)
                .availableSeats(Math.max(0, effectiveCapacity - confirmed))
                .organizerId(organizer == null ? null : organizer.getId())
                .organizerName(organizer == null ? null : organizer.getName())
                .posterUrl(posterUrl)
                .id(event.getId())
                .name(event.getName())
                .eventDate(event.getEventDate())
                .eventTime(event.getEventTime())
                .location(event.getLocation())
                .type(event.getType())
                .budget(event.getBudget())
                .status(event.getStatus())
                .attendees(event.getAttendees())
                .description(event.getDescription())
                .contactEmail(event.getContactEmail())
                .participants(participantDTOs)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private Event convertToEntity(EventDTO dto) {
        if (dto == null) {
            return null;
        }

        Event event = Event.builder()
                .name(dto.getName())
                .eventDate(dto.getEventDate())
                .eventTime(dto.getEventTime())
                .location(dto.getLocation())
                .type(dto.getType())
                .budget(dto.getBudget())
                .status(dto.getStatus())
                .attendees(dto.getAttendees())
                .description(dto.getDescription())
                .contactEmail(dto.getContactEmail())
                .capacity(dto.getCapacity() != null ? dto.getCapacity() : dto.getAttendees())
                .participants(new ArrayList<>())
                .build();

        event.setDepartment(dto.getDepartment() == null || dto.getDepartment().isBlank() ? null : dto.getDepartment());
        event.setEndTime(dto.getEndTime());
        event.setRegistrationDeadline(dto.getRegistrationDeadline());

        if (dto.getParticipants() != null && !dto.getParticipants().isEmpty()) {
            for (ParticipantDTO participantDTO : dto.getParticipants()) {
                Participant participant = Participant.builder()
                        .name(participantDTO.getName())
                        .role(participantDTO.getRole())
                        .event(event)
                        .build();
                event.getParticipants().add(participant);
            }
        }

        return event;
    }

    private void updateEventFields(Event event, EventDTO dto) {
        if (event == null || dto == null) {
            return;
        }

        event.setName(dto.getName());
        event.setEventDate(dto.getEventDate());
        event.setEventTime(dto.getEventTime());
        event.setLocation(dto.getLocation());
        event.setType(dto.getType());
        event.setBudget(dto.getBudget());
        event.setStatus(dto.getStatus());
        event.setAttendees(dto.getAttendees());
        event.setDescription(dto.getDescription());
        event.setContactEmail(dto.getContactEmail());
        if (dto.getCapacity() != null) {
            event.setCapacity(dto.getCapacity());
        }
        event.setDepartment(dto.getDepartment() == null || dto.getDepartment().isBlank() ? null : dto.getDepartment());
        event.setEndTime(dto.getEndTime());
        event.setRegistrationDeadline(dto.getRegistrationDeadline());
    }

    private ParticipantDTO convertParticipantToDTO(Participant participant) {
        if (participant == null) {
            return null;
        }

        return ParticipantDTO.builder()
                .id(participant.getId())
                .name(participant.getName())
                .role(participant.getRole())
                .build();
    }
}
