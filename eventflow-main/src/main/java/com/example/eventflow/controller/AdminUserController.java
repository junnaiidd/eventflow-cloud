package com.example.eventflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.AuthRequests;
import com.example.eventflow.dto.UserDTO;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.UserService;

import jakarta.validation.Valid;

/** Admin-only account management (this is how organizer accounts are created). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDTO> list() {
        return userService.listAll();
    }

    /** Activate or deactivate an account (deactivated accounts cannot sign in). */
    @PostMapping("/{id}/active")
    public UserDTO setActive(@PathVariable Long id, @Valid @RequestBody AuthRequests.SetActive body,
            @AuthenticationPrincipal AppUserDetails me) {
        return userService.setActive(id, body.active(), me.getId());
    }

    @PostMapping
    public ResponseEntity<UserDTO> create(@Valid @RequestBody AuthRequests.CreateUser body) {
        UserDTO created = userService.create(body.name(), body.email(), body.password(), body.role(),
                body.usn(), body.department(), body.yearOfStudy());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
