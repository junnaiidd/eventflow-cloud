package com.example.eventflow.model;

public enum RegistrationStatus {
    /** Holds a seat and a ticket. */
    CONFIRMED,
    /** Event was full; waiting in queue order for a seat. */
    WAITLISTED,
    /** Cancelled by the participant. Tickets of cancelled registrations are never accepted. */
    CANCELLED
}
