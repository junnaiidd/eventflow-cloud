package com.example.eventflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.eventflow.model.Role;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.service.UserService;

/**
 * Creates login accounts at start-up.
 * <ul>
 *   <li>{@code app.seed.demo-accounts=true} (local default): demo admin, organizer and participants with
 *       well-known passwords. Never enabled in the "prod" profile.</li>
 *   <li>{@code app.admin.email} + {@code app.admin.password} (env ADMIN_EMAIL / ADMIN_PASSWORD): creates the
 *       first admin if that e-mail does not exist yet. This is how production gets its first account.</li>
 * </ul>
 */
@Component
@Order(1)
public class AccountSeeder implements CommandLineRunner {

    public static final String DEMO_ADMIN = "admin@eventflow.local";
    public static final String DEMO_ORGANIZER = "organizer@eventflow.local";
    public static final String DEMO_PASSWORD_ADMIN = "Admin@123";
    public static final String DEMO_PASSWORD_ORGANIZER = "Organizer@123";
    public static final String DEMO_PASSWORD_PARTICIPANT = "Participant@123";

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    private final UserService userService;
    private final AppUserRepository users;
    private final boolean demoAccounts;
    private final String adminEmail;
    private final String adminPassword;

    public AccountSeeder(UserService userService, AppUserRepository users,
            @Value("${app.seed.demo-accounts:false}") boolean demoAccounts,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.userService = userService;
        this.users = users;
        this.demoAccounts = demoAccounts;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (demoAccounts) {
            createIfMissing("Demo Admin", DEMO_ADMIN, DEMO_PASSWORD_ADMIN, Role.ADMIN);
            createIfMissing("Demo Organizer", DEMO_ORGANIZER, DEMO_PASSWORD_ORGANIZER, Role.ORGANIZER);
            createStudent("Aarav Sharma", "aarav", "1XX22CS001", "Computer Science & Engineering", 3);
            createStudent("Diya Patel", "diya", "1XX22IS014", "Information Science & Engineering", 3);
            createStudent("Rohan Mehta", "rohan", "1XX23CS047", "Computer Science & Engineering", 2);
            createStudent("Isha Nair", "isha", "1XX23AD009", "AI & Data Science", 2);
            createStudent("Kabir Singh", "kabir", "1XX24ME021", "Mechanical Engineering", 1);
            createStudent("Meera Iyer", "meera", "1XX24EC033", "Electronics & Communication", 1);
            log.info("Demo accounts ready (local development only)");
        }
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            createIfMissing("Administrator", adminEmail, adminPassword, Role.ADMIN);
        }
    }

    private void createStudent(String name, String emailPrefix, String usn, String department, int year) {
        String email = emailPrefix + "@eventflow.local";
        if (!users.existsByEmail(email)) {
            userService.create(name, email, DEMO_PASSWORD_PARTICIPANT, Role.PARTICIPANT, usn, department, year);
        }
    }

    private void createIfMissing(String name, String email, String password, Role role) {
        if (!users.existsByEmail(UserService.normalizeEmail(email))) {
            userService.create(name, email, password, role);
            log.info("Created {} account {}", role, email);
        }
    }
}
