package com.example.eventflow.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class CertificateDTOs {

    private CertificateDTOs() {
    }

    /** A certificate that exists (shown on the participant's dashboard). */
    public record Summary(Long id, String certificateCode, String participantName, String eventName,
            LocalDate eventDate, String organizerName, Instant issuedAt) {
    }

    /** One row of the organizer's per-event certificate status table. status: ISSUED, PENDING or NOT_ATTENDED. */
    public record StatusRow(Long registrationId, String participantName, String participantEmail,
            boolean checkedIn, String status, Long certificateId, String certificateCode, Instant issuedAt) {
    }

    public record EventStatus(Long eventId, String eventName, long eligible, long issued, long notAttended,
            java.util.List<StatusRow> rows) {
    }
}
