package com.example.eventflow.config;

import java.time.LocalDate;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Participant;
import com.example.eventflow.repository.AppUserRepository;
import com.example.eventflow.repository.EventRepository;

/**
 * Sample college events.
 * Only runs when app.seed.enabled=true (local default; off in the "prod" profile) and the events table is
 * still empty, so a persistent database never gets duplicate sample events on restart.
 * Dates are relative to today so the demo always shows upcoming / completed events.
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
                log.info("Initializing sample college events...");
                LocalDate today = LocalDate.now();
                AppUser organizer = appUserRepository.findByEmail(AccountSeeder.DEMO_ORGANIZER).orElse(null);

                Event technova = event("TechNova 2026 - Technical Symposium", "Technical Event", "Computer Science & Engineering",
                                today.plusDays(30), 9, 17, today.plusDays(25), "Main Auditorium", 500, 500, "Upcoming", 150000.0,
                                "The annual technical symposium with paper presentations, project expos and technical quizzes.",
                                "technova@college.local", organizer);
                technova.addParticipant(Participant.builder().name("Dr. Meena Krishnan").role("Faculty Coordinator").build());
                technova.addParticipant(Participant.builder().name("Rahul Verma").role("Student Coordinator").build());

                Event fest = event("Rhythm - Inter-College Cultural Fest", "Cultural Event", null,
                                today, 17, 21, today.minusDays(1), "Open Air Theatre", 2000, 2000, "In Progress", 300000.0,
                                "Music, dance and drama competitions with teams from neighbouring colleges.",
                                "culturals@college.local", organizer);
                fest.addParticipant(Participant.builder().name("Sneha Kulkarni").role("Cultural Secretary").build());

                Event python = event("Python Workshop for Beginners", "Workshop", "Information Science & Engineering",
                                today.plusDays(7), 10, 13, today.plusDays(5), "Computer Lab 3", 50, 50, "Upcoming", 15000.0,
                                "A hands-on introduction to Python programming for first and second year students.",
                                "ise@college.local", organizer);
                python.addParticipant(Participant.builder().name("Prof. Suresh Rao").role("Resource Person").build());

                Event lecture = event("Guest Lecture: The Future of Machine Learning", "Guest Lecture", "AI & Data Science",
                                today.minusDays(30), 11, 13, today.minusDays(32), "Seminar Hall", 200, 200, "Completed", 20000.0,
                                "An industry expert talks about how machine learning is changing engineering careers.",
                                "aids@college.local", organizer);
                lecture.addParticipant(Participant.builder().name("Anita Desai").role("Guest Speaker").build());

                Event hackathon = event("HackSprint - 12 Hour Hackathon", "Hackathon", "Computer Science & Engineering",
                                today.plusDays(14), 9, 21, today.plusDays(10), "Innovation Lab", 150, 150, "Upcoming", 80000.0,
                                "Teams build a working prototype in 12 hours. Prizes for the top three projects.",
                                "hacksprint@college.local", organizer);
                hackathon.addParticipant(Participant.builder().name("Karthik Nair").role("Technical Lead").build());

                Event sports = event("Annual Sports Meet", "Sports Event", null,
                                today.minusDays(45), 8, 17, today.minusDays(50), "College Ground", 300, 300, "Completed", 120000.0,
                                "Track and field events and inter-department team sports.",
                                "sports@college.local", organizer);
                sports.addParticipant(Participant.builder().name("Coach R. Patil").role("Sports Director").build());

                Event lab = event("Spring Boot Hands-on Lab", "Workshop", "Computer Science & Engineering",
                                today.plusDays(5), 11, 14, today.plusDays(4), "Computer Lab 2, CSE Block", 3, 3, "Upcoming", 25000.0,
                                "A small hands-on lab with only 3 seats. Register to get a QR ticket; when it is full you join the waitlist.",
                                "lab@college.local", organizer);
                lab.addParticipant(Participant.builder().name("Prof. Anita Rao").role("Facilitator").build());

                // Submitted by the organizer, waiting for the administrator (shown on the admin Approvals page)
                Event robotics = event("Robotics Seminar", "Seminar", "Mechanical Engineering",
                                today.plusDays(20), 14, 16, today.plusDays(18), "Seminar Hall", 80, 80, "Pending Approval", 10000.0,
                                "Seminar on industrial robotics and automation.",
                                "mech@college.local", organizer);

                for (Event e : new Event[] { technova, fest, python, lecture, hackathon, sports, lab, robotics }) {
                        eventRepository.save(e);
                }
                log.info("Sample data initialized successfully! Total events: {}", eventRepository.count());
        }

        private static Event event(String name, String category, String department, LocalDate date, int startHour, int endHour,
                        LocalDate deadline, String venue, int attendees, int capacity, String status, double budget,
                        String description, String contact, AppUser organizer) {
                Event e = Event.builder()
                                .name(name)
                                .eventDate(date)
                                .eventTime(LocalTime.of(startHour, 0))
                                .location(venue)
                                .type(category)
                                .budget(budget)
                                .status(status)
                                .attendees(attendees)
                                .capacity(capacity)
                                .description(description)
                                .contactEmail(contact)
                                .organizer(organizer)
                                .build();
                e.setDepartment(department);
                e.setEndTime(LocalTime.of(endHour, 0));
                e.setRegistrationDeadline(deadline);
                return e;
        }
}
