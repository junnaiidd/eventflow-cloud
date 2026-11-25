package com.example.eventflow.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for Event
 * Used for API requests and responses
 * FIX #5: Added validation annotations to prevent invalid data from being saved
 */
public class EventDTO {

    private Long id;

    @NotBlank(message = "Event name cannot be empty")
    @Size(min = 3, max = 200, message = "Event name must be between 3 and 200 characters")
    private String name;

    @NotNull(message = "Event date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate eventDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime eventTime;

    @NotBlank(message = "Location is required")
    private String location;

    @NotBlank(message = "Event type is required")
    private String type;

    @NotNull(message = "Budget is required")
    @DecimalMin(value = "0.0", message = "Budget must be non-negative")
    private Double budget;

    @NotBlank(message = "Status is required")
    private String status;

    private Integer attendees;
    private String description;
    private String contactEmail;
    private List<ParticipantDTO> participants;

    /** Maximum confirmed registrations (writable). */
    @Min(value = 0, message = "Capacity must not be negative")
    @Max(value = 1000000, message = "Capacity is too large")
    private Integer capacity;

    // ---- Read-only fields filled in by the service (ignored when sent by a client) ----
    private Long organizerId;
    private String organizerName;
    private String posterUrl;
    private Integer confirmedCount;
    private Integer waitlistedCount;
    private Integer availableSeats;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate updatedAt;

    public EventDTO() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public String getLocation() {
        return location;
    }

    public String getType() {
        return type;
    }

    public Double getBudget() {
        return budget;
    }

    public String getStatus() {
        return status;
    }

    public Integer getAttendees() {
        return attendees;
    }

    public String getDescription() {
        return description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public List<ParticipantDTO> getParticipants() {
        return participants;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setBudget(Double budget) {
        this.budget = budget;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAttendees(Integer attendees) {
        this.attendees = attendees;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public void setParticipants(List<ParticipantDTO> participants) {
        this.participants = participants;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Long getOrganizerId() { return organizerId; }
    public void setOrganizerId(Long organizerId) { this.organizerId = organizerId; }
    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public Integer getConfirmedCount() { return confirmedCount; }
    public void setConfirmedCount(Integer confirmedCount) { this.confirmedCount = confirmedCount; }
    public Integer getWaitlistedCount() { return waitlistedCount; }
    public void setWaitlistedCount(Integer waitlistedCount) { this.waitlistedCount = waitlistedCount; }
    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public static EventDTOBuilder builder() {
        return new EventDTOBuilder();
    }

    public static class EventDTOBuilder {
        private Long id;
        private String name;
        private LocalDate eventDate;
        private LocalTime eventTime;
        private String location;
        private String type;
        private Double budget;
        private String status;
        private Integer attendees;
        private String description;
        private String contactEmail;
        private List<ParticipantDTO> participants;
        private LocalDate createdAt;
        private LocalDate updatedAt;
        private Integer capacity;
        private Long organizerId;
        private String organizerName;
        private String posterUrl;
        private Integer confirmedCount;
        private Integer waitlistedCount;
        private Integer availableSeats;

        public EventDTOBuilder capacity(Integer v) { this.capacity = v; return this; }
        public EventDTOBuilder organizerId(Long v) { this.organizerId = v; return this; }
        public EventDTOBuilder organizerName(String v) { this.organizerName = v; return this; }
        public EventDTOBuilder posterUrl(String v) { this.posterUrl = v; return this; }
        public EventDTOBuilder confirmedCount(Integer v) { this.confirmedCount = v; return this; }
        public EventDTOBuilder waitlistedCount(Integer v) { this.waitlistedCount = v; return this; }
        public EventDTOBuilder availableSeats(Integer v) { this.availableSeats = v; return this; }

        public EventDTOBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public EventDTOBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EventDTOBuilder eventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public EventDTOBuilder eventTime(LocalTime eventTime) {
            this.eventTime = eventTime;
            return this;
        }

        public EventDTOBuilder location(String location) {
            this.location = location;
            return this;
        }

        public EventDTOBuilder type(String type) {
            this.type = type;
            return this;
        }

        public EventDTOBuilder budget(Double budget) {
            this.budget = budget;
            return this;
        }

        public EventDTOBuilder status(String status) {
            this.status = status;
            return this;
        }

        public EventDTOBuilder attendees(Integer attendees) {
            this.attendees = attendees;
            return this;
        }

        public EventDTOBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EventDTOBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public EventDTOBuilder participants(List<ParticipantDTO> participants) {
            this.participants = participants;
            return this;
        }

        public EventDTOBuilder createdAt(LocalDate createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public EventDTOBuilder updatedAt(LocalDate updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public EventDTO build() {
            EventDTO dto = new EventDTO();
            dto.id = this.id;
            dto.name = this.name;
            dto.eventDate = this.eventDate;
            dto.eventTime = this.eventTime;
            dto.location = this.location;
            dto.type = this.type;
            dto.budget = this.budget;
            dto.status = this.status;
            dto.attendees = this.attendees;
            dto.description = this.description;
            dto.contactEmail = this.contactEmail;
            dto.participants = this.participants;
            dto.createdAt = this.createdAt;
            dto.updatedAt = this.updatedAt;
            dto.capacity = this.capacity;
            dto.organizerId = this.organizerId;
            dto.organizerName = this.organizerName;
            dto.posterUrl = this.posterUrl;
            dto.confirmedCount = this.confirmedCount;
            dto.waitlistedCount = this.waitlistedCount;
            dto.availableSeats = this.availableSeats;
            return dto;
        }
    }
}
