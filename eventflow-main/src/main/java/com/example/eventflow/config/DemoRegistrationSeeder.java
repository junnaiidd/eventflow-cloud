package com.example.eventflow.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Registration;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.EventRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.CheckInService;
import com.example.eventflow.service.RegistrationService;

/**
 * Local demo only (app.seed.demo-accounts=true): registers the demo participants for a few sample events
 * through the normal services, so tickets, the waitlist, check-ins and certificates exist for the demo and
 * the analytics dashboard has real rows to show. Registration dates are spread over the last few days.
 */
@Component
@Order(3)
public class DemoRegistrationSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRegistrationSeeder.class);
    private static final List<String> PARTICIPANTS = List.of("aarav", "diya", "rohan", "isha", "kabir", "meera");

    private final boolean demoAccounts;
    private final EventRepository events;
    private final AppUserRepository users;
    private final RegistrationRepository registrations;
    private final RegistrationService registrationService;
    private final CheckInService checkInService;

    public DemoRegistrationSeeder(@Value("${app.seed.demo-accounts:false}") boolean demoAccounts,
            EventRepository events, AppUserRepository users, RegistrationRepository registrations,
            RegistrationService registrationService, CheckInService checkInService) {
        this.demoAccounts = demoAccounts;
        this.events = events;
        this.users = users;
        this.registrations = registrations;
        this.registrationService = registrationService;
        this.checkInService = checkInService;
    }

    @Override
    public void run(String... args) {
        if (!demoAccounts || registrations.count() > 0) {
            return;
        }
        AppUser organizer = users.findByEmail(AccountSeeder.DEMO_ORGANIZER).orElse(null);
        if (organizer == null) {
            return;
        }
        AppUserDetails organizerDetails = new AppUserDetails(organizer);

        // Lab (3 seats): 5 people -> 3 confirmed + 2 waitlisted; two of the confirmed attend and get certificates
        Event lab = find("Spring Boot Hands-on Lab");
        if (lab != null) {
            List<RegistrationDTO> labRegs = register(lab, PARTICIPANTS.subList(0, 5), 5);
            checkInService.checkIn(lab.getId(), labRegs.get(0).ticketId(), organizerDetails);
            checkInService.checkIn(lab.getId(), labRegs.get(1).ticketId(), organizerDetails);
        }
        Event conference = find("Annual Tech Conference 2025");
        if (conference != null) {
            register(conference, PARTICIPANTS.subList(0, 4), 6);
        }
        Event workshop = find("Corporate Leadership Workshop");
        if (workshop != null) {
            register(workshop, PARTICIPANTS.subList(1, 4), 3);
        }
        Event launch = find("Product Launch Event");
        if (launch != null) {
            register(launch, PARTICIPANTS.subList(2, 4), 2);
        }
        log.info("Demo registrations created");
    }

    private Event find(String name) {
        return events.findAll().stream().filter(e -> name.equals(e.getName())).findFirst().orElse(null);
    }

    /** Registers the users in order; earlier users get older timestamps (spread over {@code daysBack} days). */
    private List<RegistrationDTO> register(Event event, List<String> userKeys, int daysBack) {
        Instant now = Instant.now();
        List<RegistrationDTO> result = new java.util.ArrayList<>();
        for (int i = 0; i < userKeys.size(); i++) {
            AppUser user = users.findByEmail(userKeys.get(i) + "@eventflow.local").orElseThrow();
            RegistrationDTO dto = registrationService.register(event.getId(), user.getId());
            Registration reg = registrations.findById(dto.id()).orElseThrow();
            Instant when = now.minus((long) (userKeys.size() - i) * daysBack * 24 / userKeys.size(), ChronoUnit.HOURS);
            reg.setRegisteredAt(when);
            reg.setQueuedAt(when);
            registrations.save(reg);
            result.add(dto);
        }
        return result;
    }
}
