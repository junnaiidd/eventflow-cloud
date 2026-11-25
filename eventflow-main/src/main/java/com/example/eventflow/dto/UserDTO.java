package com.example.eventflow.dto;

import java.time.Instant;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Role;

/** Public view of an account. The password hash is never exposed. */
public record UserDTO(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserDTO from(AppUser user) {
        return new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
