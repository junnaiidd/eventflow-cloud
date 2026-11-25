package com.example.eventflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.service.RegistrationService;

/** HTTP-level checks of authentication, roles and ownership. */
@AutoConfigureMockMvc
class SecurityApiTest extends TestSupport {

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registrations;

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/registrations/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void staticPageAndHealthArePublic() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void participantCannotUseOrganizerFeatures() throws Exception {
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(newUser("Olivia Organizer", Role.ORGANIZER), 5);

        mvc.perform(get("/api/analytics/overview").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/events/analytics").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/events/" + e.getId() + "/registrations").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/events/" + e.getId() + "/certificates").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/checkin").with(user(asPrincipal(p))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + e.getId() + ",\"ticketId\":\"EF-AAAA-BBBB-CCCC\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestsNeedCsrfToken() throws Exception {
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(newUser("Olivia Organizer", Role.ORGANIZER), 5);
        mvc.perform(post("/api/events/" + e.getId() + "/register").with(user(asPrincipal(p)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/events/" + e.getId() + "/register").with(user(asPrincipal(p))).with(csrf())).andExpect(status().isCreated());
    }

    @Test
    void organizerChecksInOnlyForOwnEvents() throws Exception {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser other = newUser("Other Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        String ticket = registrations.register(e.getId(), p.getId()).ticketId();
        String body = "{\"eventId\":" + e.getId() + ",\"ticketId\":\"" + ticket + "\"}";

        mvc.perform(post("/api/checkin").with(user(asPrincipal(other))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/checkin").with(user(asPrincipal(org))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/checkin").with(user(asPrincipal(org))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"));
        mvc.perform(post("/api/checkin").with(user(asPrincipal(org))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + e.getId() + ",\"ticketId\":\"garbage\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVALID_TICKET"));
    }

    @Test
    void organizerCannotEditOrDeleteSomeoneElsesEvent() throws Exception {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser other = newUser("Other Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 5);
        mvc.perform(org_delete(e.getId(), other)).andExpect(status().isForbidden());
        mvc.perform(org_delete(e.getId(), org)).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder org_delete(Long id, AppUser actor) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/events/" + id)
                .with(user(asPrincipal(actor))).with(csrf());
    }

    @Test
    void ticketAndQrAreOnlyAvailableToTheirOwner() throws Exception {
        AppUser organizer = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        AppUser stranger = newUser("Sam Stranger", Role.PARTICIPANT);
        Event e = newEvent(organizer, 5);
        Long regId = registrations.register(e.getId(), p.getId()).id();

        mvc.perform(get("/api/registrations/" + regId + "/qr.png").with(user(asPrincipal(p)))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/registrations/" + regId + "/ticket.pdf").with(user(asPrincipal(p)))).andExpect(status().isOk());
        mvc.perform(get("/api/registrations/" + regId + "/qr.png").with(user(asPrincipal(stranger)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/registrations/" + regId + "/ticket.pdf").with(user(asPrincipal(stranger)))).andExpect(status().isNotFound());
    }

    @Test
    void loginRejectsWrongPasswordWithGenericMessage() throws Exception {
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + p.getEmail() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Incorrect email or password"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + p.getEmail() + "\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PARTICIPANT"));
    }

    @Test
    void selfSignUpAlwaysCreatesAParticipant() throws Exception {
        String email = "newbie." + System.nanoTime() + "@test.local";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Newbie\",\"email\":\"" + email + "\",\"password\":\"LongEnough1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("PARTICIPANT"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Newbie\",\"email\":\"" + email + "\",\"password\":\"LongEnough1\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
