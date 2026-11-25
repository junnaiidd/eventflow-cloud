package com.example.eventflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.example.eventflow.exception.ApiException;
import com.example.eventflow.model.AppUser;
import com.example.eventflow.model.Event;
import com.example.eventflow.model.Role;
import com.example.eventflow.service.PosterService;

class PosterServiceTest extends TestSupport {

    @Autowired PosterService posters;

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3 };

    @Test
    void validPosterIsStoredAndOnlyTheKeyIsSavedInTheDatabase() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 5);

        posters.upload(e.getId(), new MockMultipartFile("file", "my photo.png", "image/png", PNG), asPrincipal(org));

        String key = events.findById(e.getId()).orElseThrow().getPosterKey();
        assertNotNull(key);
        assertEquals(true, key.startsWith("posters/event-" + e.getId() + "-") && key.endsWith(".png"));
        assertEquals("image/png", posters.load(e.getId()).contentType());
    }

    @Test
    void wrongTypeFakeImageAndOversizedFilesAreRejected() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        Event e = newEvent(org, 5);
        var actor = asPrincipal(org);

        assertThrows(ApiException.class, () -> posters.upload(e.getId(), new MockMultipartFile("file", "x.gif", "image/gif", PNG), actor));
        assertThrows(ApiException.class, () -> posters.upload(e.getId(), new MockMultipartFile("file", "x.html", "text/html", "<script>".getBytes()), actor));
        assertThrows(ApiException.class, () -> posters.upload(e.getId(),
                new MockMultipartFile("file", "x.png", "image/png", "<html>not an image at all</html>".getBytes()), actor));
        assertThrows(ApiException.class, () -> posters.upload(e.getId(), new MockMultipartFile("file", "x.png", "image/png", new byte[0]), actor));
        byte[] big = new byte[3 * 1024 * 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThrows(ApiException.class, () -> posters.upload(e.getId(), new MockMultipartFile("file", "x.png", "image/png", big), actor));
    }

    @Test
    void onlyTheEventOwnerOrAnAdminMayUpload() {
        AppUser org = newUser("Olivia Organizer", Role.ORGANIZER);
        AppUser other = newUser("Other Organizer", Role.ORGANIZER);
        AppUser participant = newUser("Pat Participant", Role.PARTICIPANT);
        Event e = newEvent(org, 5);
        var file = new MockMultipartFile("file", "x.png", "image/png", PNG);

        assertThrows(ApiException.class, () -> posters.upload(e.getId(), file, asPrincipal(other)));
        assertThrows(ApiException.class, () -> posters.upload(e.getId(), file, asPrincipal(participant)));
    }
}
