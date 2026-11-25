package com.example.eventflow.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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

    private static final Pattern USN = Pattern.compile("^[A-Z0-9]{5,20}$");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final CollegeCatalog catalog;

    public UserService(AppUserRepository users, PasswordEncoder encoder, CollegeCatalog catalog) {
        this.users = users;
        this.encoder = encoder;
        this.catalog = catalog;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Account without a student profile (admins / organizers). */
    @Transactional
    public UserDTO create(String name, String email, String rawPassword, Role role) {
        return create(name, email, rawPassword, role, null, null, null);
    }

    /** Students must have a complete, valid profile; for other roles the profile is optional. */
    @Transactional
    public UserDTO create(String name, String email, String rawPassword, Role role, String usn, String department,
            Integer yearOfStudy) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser user = new AppUser(name.trim(), normalized, encoder.encode(rawPassword), role);
        applyProfile(user, usn, department, yearOfStudy, role == Role.PARTICIPANT);
        return UserDTO.from(users.save(user));
    }

    /** Updates the caller's own name and (for students) USN, department and year of study. */
    @Transactional
    public UserDTO updateProfile(Long userId, String name, String usn, String department, Integer yearOfStudy) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        user.setName(name.trim());
        applyProfile(user, usn, department, yearOfStudy, user.getRole() == Role.PARTICIPANT);
        return UserDTO.from(users.save(user));
    }

    @Transactional
    public UserDTO setActive(Long userId, boolean active, Long actingAdminId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!active && user.getId().equals(actingAdminId)) {
            throw ApiException.conflict("You cannot deactivate your own account");
        }
        user.setActive(active);
        return UserDTO.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserDTO get(Long userId) {
        return UserDTO.from(users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
    }

    @Transactional(readOnly = true)
    public List<UserDTO> listAll() {
        return users.findAll().stream().map(UserDTO::from).toList();
    }

    private void applyProfile(AppUser user, String usn, String department, Integer year, boolean required) {
        boolean any = (usn != null && !usn.isBlank()) || (department != null && !department.isBlank()) || year != null;
        if (!any && !required) {
            return;
        }
        String normalizedUsn = usn == null ? "" : usn.trim().toUpperCase(Locale.ROOT);
        if (!USN.matcher(normalizedUsn).matches()) {
            throw ApiException.badRequest("USN must be 5 to 20 letters or digits (no spaces or symbols)");
        }
        if (!catalog.isDepartment(department)) {
            throw ApiException.badRequest("Choose a department from the list");
        }
        if (!catalog.isYear(year)) {
            throw ApiException.badRequest("Year of study must be between 1 and " + catalog.years().size());
        }
        boolean taken = user.getId() == null ? users.existsByUsn(normalizedUsn) : users.existsByUsnAndIdNot(normalizedUsn, user.getId());
        if (taken) {
            throw ApiException.conflict("This USN is already registered");
        }
        user.setUsn(normalizedUsn);
        user.setDepartment(department);
        user.setYearOfStudy(year);
    }
}
