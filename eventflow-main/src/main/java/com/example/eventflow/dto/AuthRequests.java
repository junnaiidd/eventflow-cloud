package com.example.eventflow.dto;

import com.example.eventflow.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for the authentication and user-management endpoints. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Login(
            @NotBlank(message = "Email is required") @Size(max = 190) String email,
            @NotBlank(message = "Password is required") @Size(max = 200) String password) {
    }

    public record Register(
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
            @Size(max = 190, message = "Email is too long") String email,
            // BCrypt only uses the first 72 bytes, so longer passwords are rejected rather than silently truncated
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {
    }

    public record CreateUser(
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
            @Size(max = 190, message = "Email is too long") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotNull(message = "Role is required") Role role) {
    }
}
