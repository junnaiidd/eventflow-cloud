package com.example.eventflow.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.RegistrationService;

/** Registration, waitlist and ticket endpoints. */
@RestController
public class RegistrationController {

    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    /** Register for an event (CONFIRMED with a ticket, or WAITLISTED when full). */
    @PostMapping("/api/events/{eventId}/register")
    public ResponseEntity<RegistrationDTO> register(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.register(eventId, me.getId()));
    }

    @GetMapping("/api/registrations/mine")
    public List<RegistrationDTO> mine(@AuthenticationPrincipal AppUserDetails me) {
        return registrations.listForUser(me.getId());
    }

    @GetMapping("/api/registrations/{id}")
    public RegistrationDTO one(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return registrations.get(id, me);
    }

    @PostMapping("/api/registrations/{id}/cancel")
    public RegistrationDTO cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return registrations.cancel(id, me);
    }

    @GetMapping("/api/registrations/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(registrations.ticketQr(id, me));
    }

    @GetMapping("/api/registrations/{id}/ticket.pdf")
    public ResponseEntity<byte[]> ticketPdf(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        byte[] pdf = registrations.ticketPdf(id, me);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("eventflow-ticket-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    /** Roster for organizers/admins (role + ownership enforced). */
    @GetMapping("/api/events/{eventId}/registrations")
    public List<RegistrationDTO> roster(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        return registrations.listForEvent(eventId, me);
    }
}
