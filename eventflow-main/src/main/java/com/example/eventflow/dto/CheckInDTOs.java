package com.example.eventflow.dto;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CheckInDTOs {

    private CheckInDTOs() {
    }

    public record Request(
            @NotNull(message = "Select an event first") Long eventId,
            @NotBlank(message = "Ticket ID is required") @Size(max = 100, message = "Ticket ID is too long") String ticketId) {
    }

    /** Outcome of a scan. {@code code} is one of SUCCESS, INVALID_TICKET, WRONG_EVENT, CANCELLED, ALREADY_CHECKED_IN, EVENT_CLOSED. */
    public record Result(
            boolean success,
            String code,
            String message,
            String participantName,
            String eventName,
            LocalDate eventDate,
            String ticketId,
            Instant checkedInAt,
            long checkedInCount,
            long confirmedCount) {
    }

    public record Stats(long eventId, String eventName, long confirmed, long checkedIn, long remaining,
            double attendancePercent) {
    }
}
