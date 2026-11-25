package com.example.eventflow.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.CheckInDTOs;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.CheckInService;

import jakarta.validation.Valid;

/** Attendance management for organizers/admins (role restriction is configured in SecurityConfig). */
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.of(
            CheckInService.SUCCESS, HttpStatus.OK,
            CheckInService.INVALID_TICKET, HttpStatus.NOT_FOUND,
            CheckInService.WRONG_EVENT, HttpStatus.CONFLICT,
            CheckInService.CANCELLED, HttpStatus.CONFLICT,
            CheckInService.ALREADY_CHECKED_IN, HttpStatus.CONFLICT,
            CheckInService.EVENT_CLOSED, HttpStatus.CONFLICT);

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /** Body is always a CheckInDTOs.Result; the HTTP status tells success (200) from rejection (404 / 409). */
    @PostMapping
    public ResponseEntity<CheckInDTOs.Result> checkIn(@Valid @RequestBody CheckInDTOs.Request body,
            @AuthenticationPrincipal AppUserDetails me) {
        CheckInDTOs.Result result = checkInService.checkIn(body.eventId(), body.ticketId(), me);
        return ResponseEntity.status(STATUS_BY_CODE.getOrDefault(result.code(), HttpStatus.BAD_REQUEST)).body(result);
    }

    @GetMapping("/events/{eventId}/stats")
    public CheckInDTOs.Stats stats(@PathVariable Long eventId, @AuthenticationPrincipal AppUserDetails me) {
        return checkInService.stats(eventId, me);
    }
}
