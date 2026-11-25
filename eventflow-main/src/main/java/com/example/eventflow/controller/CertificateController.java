package com.example.eventflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.CertificateDTOs;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.CertificateService;

@RestController
public class CertificateController {

    private final CertificateService certificates;

    public CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    /** The logged-in user's own certificates. */
    @GetMapping("/api/certificates/mine")
    public List<CertificateDTOs.Summary> mine(@AuthenticationPrincipal AppUserDetails me) {
        return certificates.listForUser(me.getId());
    }

    /** PDF download: owner, the event's organizer, or an admin. */
    @GetMapping("/api/certificates/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        CertificateService.PdfFile file = certificates.download(id, me);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    /** Organizer view: certificate generation status of every confirmed registrant of an event. */
    @GetMapping("/api/events/{eventId}/certificates")
    public CertificateDTOs.EventStatus eventStatus(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        return certificates.statusForEvent(eventId, me);
    }

    /** Issues certificates for attendees that do not have one yet. */
    @PostMapping("/api/events/{eventId}/certificates/generate")
    public Map<String, Integer> generate(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        return Map.of("created", certificates.issueMissing(eventId, me));
    }
}
