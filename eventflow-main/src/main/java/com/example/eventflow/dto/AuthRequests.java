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
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotBlank(message = "USN / registration number is required") @Size(max = 20, message = "USN is too long") String usn,
            @NotBlank(message = "Department is required") @Size(max = 100) String department,
            @NotNull(message = "Year of study is required") Integer yearOfStudy) {
    }

    /** Profile update. USN, department and year are required for students (checked in UserService). */
    public record Profile(
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @Size(max = 20, message = "USN is too long") String usn,
            @Size(max = 100) String department,
            Integer yearOfStudy) {
    }

    public record SetActive(@NotNull(message = "active is required") Boolean active) {
    }

    public record CreateUser(
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
            @Size(max = 190, message = "Email is too long") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 20) String usn,
            @Size(max = 100) String department,
            Integer yearOfStudy) {
    }
}
