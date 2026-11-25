package com.example.eventflow.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Event Entity
 * Represents an event in the Event Management System
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 200, message = "Event name must be between 3 and 200 characters")
    @Column(nullable = false, length = 200)
    private String name;

    @NotNull(message = "Event date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private LocalDate eventDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime eventTime;

    @NotBlank(message = "Location is required")
    @Size(max = 300)
    @Column(nullable = false, length = 300)
    private String location;

    @NotBlank(message = "Event type is required")
    @Column(nullable = false)
    private String type;

    @NotNull(message = "Budget is required")
    @Min(value = 0, message = "Budget must be positive")
    @Column(nullable = false)
    private Double budget;

    @NotBlank(message = "Status is required")
    @Column(nullable = false)
    private String status;

    @Min(value = 0, message = "Attendees must be positive")
    private Integer attendees;

    @Column(length = 1000)
    private String description;

    @Email(message = "Invalid email format")
    private String contactEmail;

    /** Maximum number of confirmed registrations. When null/0, {@link #getEffectiveCapacity()} falls back to attendees. */
    @Min(value = 0, message = "Capacity must not be negative")
    private Integer capacity;

    /** Organizing department; null/blank means open to all departments. */
    @Column(length = 100)
    private String department;

    /** End time of the event (eventTime is the start time). */
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /** Last day on which students can register (inclusive); null means until the event starts. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate registrationDeadline;

    /** The organizer who owns the event (null for legacy events, which only admins can manage). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    @JsonIgnore
    private AppUser organizer;

    /** Storage key (local path or S3 object key) of the poster image. The image itself is never stored in the DB. */
    @Column(length = 300)
    private String posterKey;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    @Column(updatable = false)
    private LocalDate createdAt;

    private LocalDate updatedAt;

    public Event() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getBudget() {
        return budget;
    }

    public void setBudget(Double budget) {
        this.budget = budget;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttendees() {
        return attendees;
    }

    public void setAttendees(Integer attendees) {
        this.attendees = attendees;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalDate getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(LocalDate registrationDeadline) { this.registrationDeadline = registrationDeadline; }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public AppUser getOrganizer() {
        return organizer;
    }

    public void setOrganizer(AppUser organizer) {
        this.organizer = organizer;
    }

    public String getPosterKey() {
        return posterKey;
    }

    public void setPosterKey(String posterKey) {
        this.posterKey = posterKey;
    }

    /**
     * Seats that can be confirmed: the explicit capacity, otherwise the legacy "expected attendees"
     * value. 0 means no capacity has been configured, so registration stays closed.
     */
    public int getEffectiveCapacity() {
        if (capacity != null && capacity > 0) {
            return capacity;
        }
        return attendees != null && attendees > 0 ? attendees : 0;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static EventBuilder builder() {
        return new EventBuilder();
    }

    public static class EventBuilder {
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
        private Integer capacity;
        private AppUser organizer;
        private List<Participant> participants;
        private LocalDate createdAt;
        private LocalDate updatedAt;

        public EventBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public EventBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EventBuilder eventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public EventBuilder eventTime(LocalTime eventTime) {
            this.eventTime = eventTime;
            return this;
        }

        public EventBuilder location(String location) {
            this.location = location;
            return this;
        }

        public EventBuilder type(String type) {
            this.type = type;
            return this;
        }

        public EventBuilder budget(Double budget) {
            this.budget = budget;
            return this;
        }

        public EventBuilder status(String status) {
            this.status = status;
            return this;
        }

        public EventBuilder attendees(Integer attendees) {
            this.attendees = attendees;
            return this;
        }

        public EventBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EventBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public EventBuilder capacity(Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        public EventBuilder organizer(AppUser organizer) {
            this.organizer = organizer;
            return this;
        }

        public EventBuilder participants(List<Participant> participants) {
            this.participants = participants;
            return this;
        }

        public EventBuilder createdAt(LocalDate createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public EventBuilder updatedAt(LocalDate updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Event build() {
            Event event = new Event();
            event.id = this.id;
            event.name = this.name;
            event.eventDate = this.eventDate;
            event.eventTime = this.eventTime;
            event.location = this.location;
            event.type = this.type;
            event.budget = this.budget;
            event.status = this.status;
            event.attendees = this.attendees;
            event.description = this.description;
            event.contactEmail = this.contactEmail;
            event.capacity = this.capacity;
            event.organizer = this.organizer;
            event.participants = this.participants != null ? this.participants : new ArrayList<>();
            event.createdAt = this.createdAt;
            event.updatedAt = this.updatedAt;
            return event;
        }
    }

    public void addParticipant(Participant participant) {
        participants.add(participant);
        participant.setEvent(this);
    }

    public void removeParticipant(Participant participant) {
        participants.remove(participant);
        participant.setEvent(null);
    }
}
