package com.example.eventflow.config;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Participant;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Initialize database with sample data.
 * Only runs when app.seed.enabled=true (local default; off in the "prod" profile) and the events table is
 * still empty, so a persistent database (RDS) never gets duplicate sample events on restart.
 * Sample dates are relative to today so the demo always shows upcoming / completed events.
 */
@Component
@Order(2)
public class DataInitializer implements CommandLineRunner {

        private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
        private final EventRepository eventRepository;
        private final AppUserRepository appUserRepository;
        private final boolean seedEnabled;

        public DataInitializer(EventRepository eventRepository, AppUserRepository appUserRepository,
                        @Value("${app.seed.enabled:true}") boolean seedEnabled) {
                this.eventRepository = eventRepository;
                this.appUserRepository = appUserRepository;
                this.seedEnabled = seedEnabled;
        }

        @Override
        public void run(String... args) {
                if (!seedEnabled) {
                        log.info("Sample data disabled (app.seed.enabled=false)");
                        return;
                }
                if (eventRepository.count() > 0) {
                        log.info("Events already exist, skipping sample data");
                        return;
                }
                log.info("Initializing sample data...");
                LocalDate today = LocalDate.now();
                // Demo organizer owns the sample events (null when demo accounts are disabled: admin-managed)
                AppUser organizer = appUserRepository.findByEmail(AccountSeeder.DEMO_ORGANIZER).orElse(null);

                // Event 1: Annual Tech Conference
                Event event1 = Event.builder()
                                .name("Annual Tech Conference 2025")
                                .eventDate(today.plusDays(30))
                                .eventTime(LocalTime.of(9, 0))
                                .location("Silicon Valley Convention Center")
                                .type("Conference")
                                .budget(5625000.0) // ₹56,25,000 (75000 USD to INR approx)
                                .status("Upcoming")
                                .attendees(500)
                                .description("A major technology conference featuring industry leaders and innovators.")
                                .contactEmail("tech@conference.com")
                                .organizer(organizer)
                                .build();

                event1.addParticipant(Participant.builder().name("John Doe").role("Speaker").build());
                event1.addParticipant(Participant.builder().name("Jane Smith").role("Organizer").build());
                event1.addParticipant(Participant.builder().name("Mike Johnson").role("Sponsor").build());

                // Event 2: Summer Music Festival
                Event event2 = Event.builder()
                                .name("Summer Music Festival")
                                .eventDate(today)
                                .eventTime(LocalTime.of(17, 0))
                                .location("Central Park Amphitheater")
                                .type("Concert")
                                .budget(9000000.0) // ₹90,00,000
                                .status("In Progress")
                                .attendees(2000)
                                .description("An outdoor music festival featuring multiple artists and food vendors.")
                                .contactEmail("info@musicfest.com")
                                .organizer(organizer)
                                .build();

                event2.addParticipant(Participant.builder().name("Sarah Williams").role("Artist").build());
                event2.addParticipant(Participant.builder().name("Tom Brown").role("Manager").build());

                // Event 3: Corporate Leadership Workshop
                Event event3 = Event.builder()
                                .name("Corporate Leadership Workshop")
                                .eventDate(today.plusDays(7))
                                .eventTime(LocalTime.of(10, 0))
                                .location("Downtown Business Center")
                                .type("Workshop")
                                .budget(1125000.0) // ₹11,25,000
                                .status("Upcoming")
                                .attendees(50)
                                .description("Professional development workshop for executives and managers.")
                                .contactEmail("contact@leadership.com")
                                .organizer(organizer)
                                .build();

                event3.addParticipant(Participant.builder().name("Emily Davis").role("Facilitator").build());
                event3.addParticipant(Participant.builder().name("Robert Wilson").role("Coordinator").build());

                // Event 4: James & Emma Wedding
                Event event4 = Event.builder()
                                .name("James & Emma Wedding")
                                .eventDate(today.minusDays(30))
                                .eventTime(LocalTime.of(16, 0))
                                .location("Seaside Resort & Spa")
                                .type("Wedding")
                                .budget(6375000.0) // ₹63,75,000
                                .status("Completed")
                                .attendees(200)
                                .description("Beautiful beachfront wedding ceremony and reception.")
                                .contactEmail("james.emma@wedding.com")
                                .organizer(organizer)
                                .build();

                event4.addParticipant(Participant.builder().name("James Anderson").role("Groom").build());
                event4.addParticipant(Participant.builder().name("Emma Taylor").role("Bride").build());

                // Event 5: Product Launch Event
                Event event5 = Event.builder()
                                .name("Product Launch Event")
                                .eventDate(today.plusDays(14))
                                .eventTime(LocalTime.of(14, 0))
                                .location("Innovation Hub")
                                .type("Corporate")
                                .budget(3375000.0) // ₹33,75,000
                                .status("Upcoming")
                                .attendees(150)
                                .description("Launch event for the new product line with demos and presentations.")
                                .contactEmail("launch@company.com")
                                .organizer(organizer)
                                .build();

                event5.addParticipant(Participant.builder().name("Alex Martinez").role("Product Manager").build());
                event5.addParticipant(Participant.builder().name("Lisa Chen").role("Marketing Director").build());

                // Event 6: Charity Gala Dinner
                Event event6 = Event.builder()
                                .name("Charity Gala Dinner")
                                .eventDate(today.minusDays(45))
                                .eventTime(LocalTime.of(19, 0))
                                .location("Grand Hotel Ballroom")
                                .type("Other")
                                .budget(4875000.0) // ₹48,75,000
                                .status("Completed")
                                .attendees(300)
                                .description("Annual charity fundraising gala with dinner and auction.")
                                .contactEmail("contact@charitygala.org")
                                .organizer(organizer)
                                .build();

                event6.addParticipant(Participant.builder().name("David Lee").role("Organizer").build());
                event6.addParticipant(Participant.builder().name("Maria Garcia").role("Volunteer").build());

                // Event 7: small-capacity lab, used to demonstrate tickets, the waitlist and certificates
                Event event7 = Event.builder()
                                .name("Spring Boot Hands-on Lab")
                                .eventDate(today.plusDays(5))
                                .eventTime(LocalTime.of(11, 0))
                                .location("Computer Lab 2, CSE Block")
                                .type("Workshop")
                                .budget(25000.0)
                                .status("Upcoming")
                                .attendees(3)
                                .capacity(3)
                                .description("A small hands-on lab with only 3 seats. Register to get a QR ticket; when it is full you join the waitlist.")
                                .contactEmail("lab@eventflow.local")
                                .organizer(organizer)
                                .build();
                event7.addParticipant(Participant.builder().name("Prof. Anita Rao").role("Facilitator").build());

                // Save all events
                eventRepository.save(event1);
                eventRepository.save(event2);
                eventRepository.save(event3);
                eventRepository.save(event4);
                eventRepository.save(event5);
                eventRepository.save(event6);
                eventRepository.save(event7);

                log.info("Sample data initialized successfully! Total events: {}", eventRepository.count());
        }
}