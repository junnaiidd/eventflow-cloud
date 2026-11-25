package com.example.eventflow.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Participation certificate. Name/event details are copied at issue time so a certificate
 * stays unchanged even if the event is later renamed. At most one per registration.
 */
@Entity
@Table(name = "certificates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_certificate_registration", columnNames = "registration_id"),
        @UniqueConstraint(name = "uk_certificate_code", columnNames = "certificate_code")
})
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Column(name = "certificate_code", nullable = false, length = 40)
    private String certificateCode;

    @Column(nullable = false, length = 120)
    private String participantName;

    @Column(nullable = false, length = 200)
    private String eventName;

    @Column(nullable = false)
    private LocalDate eventDate;

    @Column(length = 200)
    private String organizerName;

    @Column(nullable = false)
    private Instant issuedAt;

    /** Where the generated PDF is stored (local path or S3 key). Null if storage failed; PDF is rebuilt on demand. */
    @Column(length = 300)
    private String storageKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Registration getRegistration() { return registration; }
    public void setRegistration(Registration registration) { this.registration = registration; }
    public String getCertificateCode() { return certificateCode; }
    public void setCertificateCode(String certificateCode) { this.certificateCode = certificateCode; }
    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }
    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
}
