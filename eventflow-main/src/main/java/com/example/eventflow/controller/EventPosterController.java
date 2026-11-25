package com.example.eventflow.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.PosterService;
import com.example.eventflow.storage.StoredFile;

/**
 * Event poster / banner upload and download. Files are streamed through the application (private S3 bucket),
 * so the browser never receives AWS credentials or bucket URLs.
 */
@RestController
public class EventPosterController {

    private final PosterService posters;

    public EventPosterController(PosterService posters) {
        this.posters = posters;
    }

    @PostMapping("/api/events/{eventId}/poster")
    public Map<String, String> upload(@PathVariable Long eventId, @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AppUserDetails me) {
        posters.upload(eventId, file, me);
        return Map.of("status", "uploaded");
    }

    @DeleteMapping("/api/events/{eventId}/poster")
    public ResponseEntity<Void> remove(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        posters.remove(eventId, me);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/events/{eventId}/poster")
    public ResponseEntity<byte[]> get(@PathVariable Long eventId) {
        StoredFile file = posters.load(eventId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(file.content());
    }
}
