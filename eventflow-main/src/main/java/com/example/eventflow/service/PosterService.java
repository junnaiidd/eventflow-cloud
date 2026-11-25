package com.example.eventflow.service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.Event;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.security.AccessPolicy;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.storage.FileStorageService;
import com.example.eventflow.storage.StorageException;
import com.example.eventflow.storage.StorageNotFoundException;
import com.example.eventflow.storage.StoredFile;

/** Event poster / banner images: validation, storage (local disk or S3) and retrieval. */
@Service
public class PosterService {

    private static final Logger log = LoggerFactory.getLogger(PosterService.class);
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");

    private final EventRepository events;
    private final FileStorageService storage;
    private final AccessPolicy access;
    private final long maxBytes;

    public PosterService(EventRepository events, FileStorageService storage, AccessPolicy access,
            @Value("${app.upload.max-bytes:2097152}") long maxBytes) {
        this.events = events;
        this.storage = storage;
        this.access = access;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public void upload(Long eventId, MultipartFile file, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose an image to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "The image must be " + (maxBytes / 1024 / 1024) + " MB or smaller");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(declared);
        if (extension == null) {
            throw ApiException.badRequest("Only PNG, JPEG or WebP images are allowed");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read");
        }
        // Do not trust the declared type: the file's first bytes must match an allowed image format.
        if (!matchesSignature(bytes, extension)) {
            throw ApiException.badRequest("The file content does not match an image of type " + declared);
        }

        String newKey = "posters/event-" + eventId + "-" + UUID.randomUUID() + "." + extension; // never the user's file name
        String oldKey = event.getPosterKey();
        try {
            storage.put(newKey, bytes, declared);
        } catch (StorageException e) {
            log.error("Poster upload failed", e);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "The image could not be saved. Please try again.");
        }
        event.setPosterKey(newKey);
        events.save(event);
        deleteQuietly(oldKey);
    }

    @Transactional
    public void remove(Long eventId, AppUserDetails actor) {
        Event event = access.requireManageable(eventId, actor);
        String key = event.getPosterKey();
        event.setPosterKey(null);
        events.save(event);
        deleteQuietly(key);
    }

    @Transactional(readOnly = true)
    public StoredFile load(Long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (event.getPosterKey() == null) {
            throw ApiException.notFound("This event has no poster");
        }
        try {
            return storage.get(event.getPosterKey());
        } catch (StorageNotFoundException e) {
            throw ApiException.notFound("This event has no poster");
        } catch (StorageException e) {
            log.error("Poster read failed", e);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "The image is temporarily unavailable");
        }
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    static boolean matchesSignature(byte[] b, String extension) {
        if (b.length < 12) {
            return false;
        }
        return switch (extension) {
            case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "jpg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
