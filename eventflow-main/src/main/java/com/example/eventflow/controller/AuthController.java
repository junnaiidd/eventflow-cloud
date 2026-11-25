package com.example.eventflow.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.AuthRequests;
import com.example.eventflow.dto.UserDTO;
import com.example.eventflow.model.Role;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Login / logout / sign-up. Self sign-up always creates a PARTICIPANT; organizers are created by an admin. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
                    .unauthenticated(UserService.normalizeEmail(body.email()), body.password()));
            startSession(auth, request, response);
            return ResponseEntity.ok(userService.get(((AppUserDetails) auth.getPrincipal()).getId()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "This account has been deactivated. Contact the administrator."));
        } catch (AuthenticationException e) {
            // Same message for unknown e-mail and wrong password, so accounts cannot be enumerated.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Incorrect email or password"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request,
            HttpServletResponse response) {
        // Self sign-up always creates a student (internal role PARTICIPANT) with a complete profile.
        UserDTO created = userService.create(body.name(), body.email(), body.password(), Role.PARTICIPANT,
                body.usn(), body.department(), body.yearOfStudy());
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(created.email(), body.password()));
        startSession(auth, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal AppUserDetails me) {
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.get(me.getId()));
    }

    @PutMapping("/profile")
    public UserDTO updateProfile(@Valid @RequestBody AuthRequests.Profile body, @AuthenticationPrincipal AppUserDetails me) {
        return userService.updateProfile(me.getId(), body.name(), body.usn(), body.department(), body.yearOfStudy());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private void startSession(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId(); // prevents session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

}
