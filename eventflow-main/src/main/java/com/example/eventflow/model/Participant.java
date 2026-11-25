package com.example.eventflow.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Participant Entity
 * Represents a participant associated with an event
 */
@Entity
@Table(name = "participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Participant name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Role is required")
    @Column(nullable = false)
    private String role; // Speaker, Organizer, Sponsor, Artist, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    private Event event;

    // No-args constructor
    public Participant() {
    }

    // Getters and Setters
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    // Builder pattern implementation
    public static ParticipantBuilder builder() {
        return new ParticipantBuilder();
    }

    public static class ParticipantBuilder {
        private Long id;
        private String name;
        private String role;
        private Event event;

        public ParticipantBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ParticipantBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ParticipantBuilder role(String role) {
            this.role = role;
            return this;
        }

        public ParticipantBuilder event(Event event) {
            this.event = event;
            return this;
        }

        public Participant build() {
            Participant participant = new Participant();
            participant.id = this.id;
            participant.name = this.name;
            participant.role = this.role;
            participant.event = this.event;
            return participant;
        }
    }
}