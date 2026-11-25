package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.eventflow.dto.CertificateDTOs;
import com.example.eventflow.dto.RegistrationDTO;
import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Certificate;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.repository.CertificateRepository;
import com.example.eventflow.repository.RegistrationRepository;
import com.example.eventflow.service.CertificateService;
import com.example.eventflow.service.CheckInService;
import com.example.eventflow.service.RegistrationService;

class CertificateTest extends TestSupport {

    @Autowired RegistrationService registrations;
    @Autowired CheckInService checkIn;
    @Autowired CertificateService certificates;
    @Autowired CertificateRepository certificateRepository;
    @Autowired RegistrationRepository registrationRepository;
    @Autowired TransactionTemplate tx;

    /** issueFor() touches lazy relations, so it must run inside a transaction (as it does in the application). */
    private Certificate issue(Long registrationId) {
        return tx.execute(status -> certificates.issueFor(registrationRepository.findById(registrationId).orElseThrow()));
    }

    @Test
    void attendeeReceivesCertificateAutomaticallyAtCheckIn() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));

        var list = certificates.listForUser(p.getId());
        assertEquals(1, list.size());
        CertificateDTOs.Summary c = list.get(0);
        assertEquals("Pat Participant", c.participantName());
        assertEquals(e.getName(), c.eventName());
        assertEquals("Olivia Organizer", c.organizerName());
        assertTrue(c.certificateCode().startsWith("EFC-"));
        byte[] pdf = certificates.download(c.id(), asPrincipal(p)).content();
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void absentParticipantGetsNoCertificate() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());

        assertThrows(ApiException.class, () -> issue(r.id()));
        assertEquals(0, certificates.issueMissing(e.getId(), asPrincipal(org)));
        assertTrue(certificates.listForUser(p.getId()).isEmpty());
        CertificateDTOs.EventStatus status = certificates.statusForEvent(e.getId(), asPrincipal(org));
        assertEquals("NOT_ATTENDED", status.rows().get(0).status());
        assertEquals(1, status.notAttended());
    }

    @Test
    void certificateIdsAreUniqueAndNeverDuplicatedPerRegistration() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 20);
        Set<String> codes = new HashSet<>();
        Long lastRegistration = null;
        for (int i = 0; i < 8; i++) {
            AppUser p = newUser("Attendee " + i, Role.PARTICIPANT);
            RegistrationDTO r = registrations.register(e.getId(), p.getId());
            checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));
            Certificate c = issue(r.id()); // already issued at check-in: returns the existing record
            assertTrue(codes.add(c.getCertificateCode()), "duplicate certificate id");
            lastRegistration = r.id();
        }
        assertEquals(8, certificateRepository.findByEventId(e.getId()).size());
        assertEquals(issue(lastRegistration).getId(), issue(lastRegistration).getId());
        assertEquals(0, certificates.issueMissing(e.getId(), asPrincipal(org)));
    }

    @Test
    void certificateStaysUnchangedWhenEventIsRenamed() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        String originalName = e.getName();
        RegistrationDTO r = registrations.register(e.getId(), p.getId());
        checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));
        Long certId = certificates.listForUser(p.getId()).get(0).id();
        byte[] before = certificates.download(certId, asPrincipal(p)).content();

        e.setName("Renamed Event");
        events.save(e);

        assertEquals(originalName, certificates.listForUser(p.getId()).get(0).eventName());
        assertArrayEquals(before, certificates.download(certId, asPrincipal(p)).content(), "previously generated certificate is preserved");
    }

    @Test
    void onlyOwnerEventOrganizerOrAdminCanDownload() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser otherOrg = newUser("Other Organizer", Role.ORGANIZER);
        AppUser admin = newUser("Alex Admin", Role.ADMIN);
        AppUser p = newUser("Pat Participant", Role.PARTICIPANT);
        AppUser stranger = newUser("Sam Stranger", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        RegistrationDTO r = registrations.register(e.getId(), p.getId());
        checkIn.checkIn(e.getId(), r.ticketId(), asPrincipal(org));
        Long certId = certificates.listForUser(p.getId()).get(0).id();

        assertNotNull(certificates.download(certId, asPrincipal(p)));
        assertNotNull(certificates.download(certId, asPrincipal(org)));
        assertNotNull(certificates.download(certId, asPrincipal(admin)));
        assertThrows(ApiException.class, () -> certificates.download(certId, asPrincipal(stranger)));
        assertThrows(ApiException.class, () -> certificates.download(certId, asPrincipal(otherOrg)));
        assertThrows(ApiException.class, () -> certificates.statusForEvent(e.getId(), asPrincipal(otherOrg)));
        assertNotNull(certificates.statusForEvent(e.getId(), asPrincipal(org)));
    }
}
