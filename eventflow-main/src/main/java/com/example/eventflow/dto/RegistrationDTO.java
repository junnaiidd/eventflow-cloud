package com.example.eventflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.example.eventflow.model.RegistrationStatus;

/**
 * A registration as shown to participants (their tickets) and organizers (rosters).
 * {@code ticketId} is only set for CONFIRMED registrations; {@code waitlistPosition} only for WAITLISTED ones.
 */
public record RegistrationDTO(
        Long id,
        Long eventId,
        String eventName,
        LocalDate eventDate,
        LocalTime eventTime,
        String location,
        String eventStatus,
        Long userId,
        String participantName,
        String participantEmail,
        RegistrationStatus status,
        Integer waitlistPosition,
        String ticketId,
        Instant registeredAt,
        Instant cancelledAt,
        Instant checkedInAt,
        boolean checkedIn,
        Long certificateId,
        String certificateCode,
        String usn,
        String department,
        Integer yearOfStudy) {
}
