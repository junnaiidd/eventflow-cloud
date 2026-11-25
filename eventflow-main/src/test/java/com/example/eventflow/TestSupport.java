package com.example.eventflow;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.security.AppUserDetails;

/** Base class: full application context on the in-memory H2 database ("test" profile, no sample data). */
@SpringBootTest
@ActiveProfiles("test")
public abstract class TestSupport {

    @Autowired protected AppUserRepository users;
    @Autowired protected EventRepository events;
    @Autowired protected PasswordEncoder encoder;

    protected AppUser newUser(String name, Role role) {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        AppUser user = new AppUser(name, email, encoder.encode("Password@123"), role);
        if (role == Role.PARTICIPANT) { // students need a profile before they can register for events
            user.setUsn("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase());
            user.setDepartment("Computer Science & Engineering");
            user.setYearOfStudy(2);
        }
        return users.save(user);
    }

    protected Event newEvent(AppUser organizer, int capacity) {
        return events.save(Event.builder()
                .name("Test Event " + UUID.randomUUID().toString().substring(0, 6))
                .eventDate(LocalDate.now().plusDays(3))
                .location("Hall A")
                .type("Workshop")
                .budget(1000.0)
                .status("Upcoming")
                .attendees(capacity)
                .capacity(capacity)
                .organizer(organizer)
                .build());
    }

    protected static AppUserDetails asPrincipal(AppUser user) {
        return new AppUserDetails(user);
    }
}
