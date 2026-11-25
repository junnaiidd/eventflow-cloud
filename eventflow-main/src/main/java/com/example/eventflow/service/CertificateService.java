package com.example.eventflow.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.CertificateDTOs;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.Certificate;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Registration;
import com.example.eventflow.model.RegistrationStatus;
import com.example.eventflow.repository.CertificateRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.security.AccessPolicy;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.pdf.CertificatePdfGenerator;
import com.example.eventflow.storage.FileStorageService;
import com.example.eventflow.storage.StorageException;

/**
 * Participation certificates. A certificate exists only for a CONFIRMED registration that was checked in;
 * it is created automatically at check-in (or by the organizer's "generate missing" action) and is never
 * overwritten afterwards.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CertificateRepository certificates;
    private final RegistrationRepository registrations;
    private final CertificatePdfGenerator pdf;
    private final FileStorageService storage;
    private final AccessPolicy access;
    private final String defaultOrganization;
    private final ZoneId zone;
    private final SecureRandom random = new SecureRandom();

    public CertificateService(CertificateRepository certificates, RegistrationRepository registrations,
            CertificatePdfGenerator pdf, FileStorageService storage, AccessPolicy access,
            @Value("${app.organization.name:EventFlow}") String defaultOrganization,
            @Value("${spring.jackson.time-zone:Asia/Kolkata}") String timeZone) {
        this.certificates = certificates;
        this.registrations = registrations;
        this.pdf = pdf;
        this.storage = storage;
        this.access = access;
        this.defaultOrganization = defaultOrganization;
        this.zone = ZoneId.of(timeZone);
    }

    /** True if this registration may receive a certificate: confirmed AND actually attended. */
    public static boolean isEligible(Registration reg) {
        return reg.getStatus() == RegistrationStatus.CONFIRMED && reg.isCheckedIn();
    }

    /** Creates the certificate for an attended registration, or returns the existing one (idempotent). */
    @Transactional
    public Certificate issueFor(Registration reg) {
        if (!isEligible(reg)) {
            throw ApiException.conflict("Only participants who attended the event can receive a certificate");
        }
        Optional<Certificate> existing = certificates.findByRegistrationId(reg.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Event event = reg.getEvent();
        Certificate cert = new Certificate();
        cert.setRegistration(reg);
        cert.setCertificateCode(newCode());
        cert.setParticipantName(reg.getUser().getName());
        cert.setEventName(event.getName());
        cert.setEventDate(event.getEventDate());
        cert.setOrganizerName(event.getOrganizer() != null ? event.getOrganizer().getName() : defaultOrganization);
        cert.setIssuedAt(Instant.now());
        cert = certificates.saveAndFlush(cert);
        store(cert);
        log.info("Issued certificate {} for registration {}", cert.getCertificateCode(), reg.getId());
        return cert;
    }

    /** Organizer action: issues certificates for attendees who do not have one yet. Returns how many were created. */
    @Transactional
    public int issueMissing(Long eventId, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);
        int created = 0;
        for (Registration reg : registrations.findByEventIdOrderByRegisteredAtAsc(event.getId())) {
            if (isEligible(reg) && certificates.findByRegistrationId(reg.getId()).isEmpty()) {
                issueFor(reg);
                created++;
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<CertificateDTOs.Summary> listForUser(Long userId) {
        return certificates.findByUserId(userId).stream().map(CertificateService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CertificateDTOs.EventStatus statusForEvent(Long eventId, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);
        List<Registration> regs = registrations.findByEventIdOrderByRegisteredAtAsc(eventId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED).toList();
        var byReg = certificates.findByEventId(eventId).stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getRegistration().getId(), c -> c));
        long issued = 0, notAttended = 0, eligible = 0;
        List<CertificateDTOs.StatusRow> rows = new java.util.ArrayList<>();
        for (Registration r : regs) {
            Certificate c = byReg.get(r.getId());
            String status;
            if (c != null) {
                status = "ISSUED";
                issued++;
                eligible++;
            } else if (r.isCheckedIn()) {
                status = "PENDING";
                eligible++;
            } else {
                status = "NOT_ATTENDED";
                notAttended++;
            }
            rows.add(new CertificateDTOs.StatusRow(r.getId(), r.getUser().getName(), r.getUser().getEmail(),
                    r.isCheckedIn(), status, c == null ? null : c.getId(), c == null ? null : c.getCertificateCode(),
                    c == null ? null : c.getIssuedAt()));
        }
        return new CertificateDTOs.EventStatus(eventId, event.getName(), eligible, issued, notAttended, rows);
    }

    /** A downloadable PDF together with a file name. */
    public record PdfFile(byte[] content, String fileName) {
    }

    /**
     * Returns the certificate PDF. Allowed for the certificate's owner, the event's organizer and admins.
     * The stored copy is served when available; otherwise it is rebuilt from the saved snapshot.
     */
    @Transactional
    public PdfFile download(Long certificateId, AppUserDetails actor) {
        Certificate cert = certificates.findById(certificateId)
                .orElseThrow(() -> ApiException.notFound("Certificate not found"));
        Registration reg = cert.getRegistration();
        boolean owner = reg.getUser().getId().equals(actor.getId());
        if (!owner && !access.canManage(reg.getEvent(), actor)) {
            throw ApiException.notFound("Certificate not found");
        }
        byte[] bytes = null;
        if (cert.getStorageKey() != null) {
            try {
                bytes = storage.get(cert.getStorageKey()).content();
            } catch (StorageException e) {
                log.warn("Stored copy of certificate {} unavailable ({}); rebuilding", cert.getCertificateCode(), e.getMessage());
            }
        }
        if (bytes == null) {
            bytes = pdf.generate(cert);
            store(cert); // try to put a copy back into storage
        }
        return new PdfFile(bytes, "certificate-" + cert.getCertificateCode() + ".pdf");
    }

    private void store(Certificate cert) {
        try {
            String key = "certificates/" + cert.getCertificateCode() + ".pdf";
            storage.put(key, pdf.generate(cert), "application/pdf");
            cert.setStorageKey(key);
            certificates.save(cert);
        } catch (RuntimeException e) {
            // Not fatal: the PDF is rebuilt on demand from the certificate row.
            log.warn("Could not store certificate {}: {}", cert.getCertificateCode(), e.getMessage());
        }
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("EFC-").append(java.time.Year.now(zone).getValue()).append('-');
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!certificates.existsByCertificateCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique certificate id");
    }

    private static CertificateDTOs.Summary toSummary(Certificate c) {
        return new CertificateDTOs.Summary(c.getId(), c.getCertificateCode(), c.getParticipantName(), c.getEventName(),
                c.getEventDate(), c.getOrganizerName(), c.getIssuedAt());
    }
}
