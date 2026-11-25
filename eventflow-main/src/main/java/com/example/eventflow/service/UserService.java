package com.example.eventflow.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.eventflow.dto.UserDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Role;
import com.example.eventflow.repository.AppUserRepository;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserDTO create(String name, String email, String rawPassword, Role role) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser saved = users.save(new AppUser(name.trim(), normalized, encoder.encode(rawPassword), role));
        return UserDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<UserDTO> listAll() {
        return users.findAll().stream().map(UserDTO::from).toList();
    }
}
