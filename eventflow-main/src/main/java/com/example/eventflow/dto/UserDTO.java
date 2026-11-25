package com.example.eventflow.dto;

import java.time.Instant;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Role;

/** Public view of an account (never the password hash). PARTICIPANT is shown as "Student" in the UI. */
public record UserDTO(Long id, String name, String email, Role role, Instant createdAt,
        String usn, String department, Integer yearOfStudy, boolean active) {

    public static UserDTO from(AppUser u) {
        return new UserDTO(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getCreatedAt(),
                u.getUsn(), u.getDepartment(), u.getYearOfStudy(), u.isActive());
    }
}
