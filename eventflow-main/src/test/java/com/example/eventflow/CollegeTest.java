package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.eventflow.dto.EventDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.service.EventService;
import com.example.eventflow.service.RegistrationService;
import com.example.eventflow.service.UserService;

/** College-specific rules: student profile, approval workflow, deadline, account activation. */
@AutoConfigureMockMvc
class CollegeTest extends TestSupport {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired EventService eventService;
    @Autowired RegistrationService registrations;

    private EventDTO eventDto(String category) {
        return EventDTO.builder().name("Hack Night " + System.nanoTime()).eventDate(LocalDate.now().plusDays(10))
                .eventTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0)).location("Lab 1").type(category)
                .department("Computer Science & Engineering").budget(100.0).status("Upcoming").capacity(10)
                .registrationDeadline(LocalDate.now().plusDays(5)).build();
    }

    @Test
    void studentProfileIsValidatedAndUsnIsUnique() {
        String email = "stud" + System.nanoTime() + "@test.local";
        assertThrows(ApiException.class, () -> userService.create("S", email, "Password1", Role.PARTICIPANT, "12", "Computer Science & Engineering", 2));
        assertThrows(ApiException.class, () -> userService.create("S", email, "Password1", Role.PARTICIPANT, "1AB23CS777", "Underwater Basket Weaving", 2));
        assertThrows(ApiException.class, () -> userService.create("S", email, "Password1", Role.PARTICIPANT, "1AB23CS777", "Computer Science & Engineering", 9));
        assertThrows(ApiException.class, () -> userService.create("S", email, "Password1", Role.PARTICIPANT, null, null, null));

        var created = userService.create("S", email, "Password1", Role.PARTICIPANT, "1ab23cs" + (System.nanoTime() % 900 + 100), "Computer Science & Engineering", 2);
        assertEquals(created.usn(), created.usn().toUpperCase());
        assertThrows(ApiException.class, () -> userService.create("T", "other" + email, "Password1", Role.PARTICIPANT, created.usn(), "Computer Science & Engineering", 2));
        assertEquals(Role.PARTICIPANT, created.role());
    }

    @Test
    void organizerEventsNeedAdminApprovalBeforeStudentsSeeThem() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        AppUser student = newUser("Sam Student", Role.PARTICIPANT);

        EventDTO created = eventService.createEvent(eventDto("Hackathon"), asPrincipal(org));
        assertEquals(EventService.PENDING, created.getStatus(), "organizer cannot publish directly");
        assertFalse(EventService.isVisibleTo(created, asPrincipal(student)));
        assertTrue(EventService.isVisibleTo(created, asPrincipal(org)));
        assertTrue(EventService.isVisibleTo(created, asPrincipal(admin)));
        assertThrows(ApiException.class, () -> registrations.register(created.getId(), student.getId()), "pending events are closed");

        // an organizer cannot approve their own event by editing the status
        EventDTO edit = eventDto("Hackathon");
        edit.setStatus("Upcoming");
        assertEquals(EventService.PENDING, eventService.updateEvent(created.getId(), edit, asPrincipal(org)).getStatus());

        assertEquals("Upcoming", eventService.approve(created.getId()).getStatus());
        assertTrue(EventService.isVisibleTo(eventService.getEventById(created.getId()), asPrincipal(student)));
        assertEquals("CONFIRMED", registrations.register(created.getId(), student.getId()).status().name());
        assertThrows(ApiException.class, () -> eventService.approve(created.getId()), "only pending events can be approved");
    }

    @Test
    void adminEventsArePublishedAndRejectionCanBeResubmitted() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        assertEquals("Upcoming", eventService.createEvent(eventDto("Seminar"), asPrincipal(admin)).getStatus());

        EventDTO pending = eventService.createEvent(eventDto("Seminar"), asPrincipal(org));
        assertEquals(EventService.REJECTED, eventService.reject(pending.getId()).getStatus());
        assertEquals(EventService.PENDING, eventService.updateEvent(pending.getId(), eventDto("Seminar"), asPrincipal(org)).getStatus(),
                "editing a rejected event sends it back for approval");
    }

    @Test
    void collegeFieldsAreValidatedOnTheServer() {
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        assertThrows(ApiException.class, () -> eventService.createEvent(eventDto("Wedding"), asPrincipal(admin)));
        EventDTO badTime = eventDto("Workshop");
        badTime.setEndTime(LocalTime.of(9, 0));
        assertThrows(ApiException.class, () -> eventService.createEvent(badTime, asPrincipal(admin)));
        EventDTO badDeadline = eventDto("Workshop");
        badDeadline.setRegistrationDeadline(LocalDate.now().plusDays(20));
        assertThrows(ApiException.class, () -> eventService.createEvent(badDeadline, asPrincipal(admin)));
        EventDTO badDept = eventDto("Workshop");
        badDept.setDepartment("Unknown Dept");
        assertThrows(ApiException.class, () -> eventService.createEvent(badDept, asPrincipal(admin)));
    }

    @Test
    void registrationClosesAfterDeadlineAndNeedsAStudentProfile() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser student = newUser("Sam Student", Role.PARTICIPANT);
        Event late = newEvent(org, 5);
        late.setRegistrationDeadline(LocalDate.now().minusDays(1));
        events.save(late);
        assertThrows(ApiException.class, () -> registrations.register(late.getId(), student.getId()));

        Event open = newEvent(org, 5);
        AppUser noProfile = users.save(new AppUser("No Profile", "np" + System.nanoTime() + "@test.local", encoder.encode("Password1"), Role.PARTICIPANT));
        assertThrows(ApiException.class, () -> registrations.register(open.getId(), noProfile.getId()));
    }

    @Test
    void adminStatsAndApprovalEndpointsAreAdminOnly() throws Exception {
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        AppUser student = newUser("Sam Student", Role.PARTICIPANT);
        mvc.perform(get("/api/admin/stats").with(user(asPrincipal(student)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").with(user(asPrincipal(admin)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.students").isNumber()).andExpect(jsonPath("$.pendingApprovals").isNumber());
        mvc.perform(post("/api/admin/events/1/approve").with(user(asPrincipal(student))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/meta")).andExpect(status().isOk()).andExpect(jsonPath("$.categories[0]").value("Technical Event"));
    }

    @Test
    void deactivatedAccountsCannotSignIn() throws Exception {
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        AppUser student = newUser("Sam Student", Role.PARTICIPANT);
        String body = "{\"email\":\"" + student.getEmail() + "\",\"password\":\"Password@123\"}";
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        userService.setActive(student.getId(), false, admin.getId());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThrows(ApiException.class, () -> userService.setActive(admin.getId(), false, admin.getId()), "admin cannot lock themselves out");
        userService.setActive(student.getId(), true, admin.getId());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
}
