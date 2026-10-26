package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import dev.danyil.artists.ArtistRepository;
import dev.danyil.releases.ReleaseRepository;
import dev.danyil.support.TestImages;
import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import jakarta.servlet.http.Cookie;

/**
 * Songs end to end on a real PostgreSQL: track numbering after create, delete and reorder,
 * the access boundary between releases, and cascades from the release.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "storage.auto-create-bucket=false"
})
class SongIntegrationTest {

    private static final String PASSWORD = "password123!";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestImages.POSTGRES);

    static {
        POSTGRES.start();
    }

    private record TestUser(long id, Cookie[] cookies) {}

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private SongRepository songRepository;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("alice");
        bob = registerAndLogin("bob");
    }

    @AfterEach
    void cleanDatabase() {
        // Releases cascade to their songs and artist links
        releaseRepository.deleteAll();
        artistRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Helpers ----------

    private TestUser registerAndLogin(String username) throws Exception {
        MvcResult registration = mockMvc.perform(post(apiEndpoint + "/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "%s", "email": "%s@test.com", "password": "%s", "displayName": "%s" }
                    """.formatted(username, username, PASSWORD, username)))
            .andExpect(status().isCreated())
            .andReturn();
        Number id = JsonPath.read(registration.getResponse().getContentAsString(), "$.id");

        MvcResult login = mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"username\": \"%s\", \"password\": \"%s\" }".formatted(username, PASSWORD)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
        Cookie[] cookies = login.getResponse().getCookies();
        assertThat("Login must set auth cookies", cookies, is(not(emptyArray())));

        return new TestUser(id.longValue(), cookies);
    }

    private static RequestPostProcessor as(TestUser user) {
        return request -> {
            request.setCookies(user.cookies());
            return request;
        };
    }

    private long createRelease(TestUser owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(apiEndpoint + "/releases")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"%s\", \"primaryGenre\": \"POP\" }".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String songsUrl(long releaseId) {
        return apiEndpoint + "/releases/" + releaseId + "/songs";
    }

    private long createSong(TestUser owner, long releaseId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post(songsUrl(releaseId))
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"%s\", \"status\": \"WRITING\" }".formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ---------- Tracklist ----------

    @Test
    void tracklist_numbersFollowCreateDeleteAndReorder() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        long intro = createSong(alice, releaseId, "Intro");
        long title = createSong(alice, releaseId, "Night Drive");
        long outro = createSong(alice, releaseId, "Outro");

        mockMvc.perform(get(songsUrl(releaseId)).with(as(alice)))
            .andExpect(jsonPath("$[*].title", contains("Intro", "Night Drive", "Outro")))
            .andExpect(jsonPath("$[*].trackNumber", contains(1, 2, 3)));

        mockMvc.perform(delete(songsUrl(releaseId) + "/" + title).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(songsUrl(releaseId)).with(as(alice)))
            .andExpect(jsonPath("$[*].title", contains("Intro", "Outro")))
            .andExpect(jsonPath("$[*].trackNumber", contains(1, 2)));

        // Swapping two tracks: fails if a unique constraint on (release_id, position) is ever added
        mockMvc.perform(put(songsUrl(releaseId) + "/order")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"songIds\": [%d, %d] }".formatted(outro, intro)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", contains("Outro", "Intro")));

        mockMvc.perform(get(songsUrl(releaseId)).with(as(alice)))
            .andExpect(jsonPath("$[*].title", contains("Outro", "Intro")))
            .andExpect(jsonPath("$[*].trackNumber", contains(1, 2)));
    }

    @Test
    void staleOrder_isRejected_andTheTracklistStaysAsItWas() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        long intro = createSong(alice, releaseId, "Intro");
        createSong(alice, releaseId, "Outro");

        mockMvc.perform(put(songsUrl(releaseId) + "/order")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"songIds\": [%d] }".formatted(intro)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get(songsUrl(releaseId)).with(as(alice)))
            .andExpect(jsonPath("$[*].title", contains("Intro", "Outro")));
    }

    // ---------- Fields ----------

    @Test
    void createThenReplace_normalizesIsrc_andNullsClearFields() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        MvcResult created = mockMvc.perform(post(songsUrl(releaseId))
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "title": "Night Drive", "durationSeconds": 215, "isrc": "us-abc-26-00001",
                      "status": "RECORDING", "lyrics": "Neon on the windshield", "explicit": true }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isrc", is("USABC2600001")))
            .andExpect(jsonPath("$.explicit", is(true)))
            .andReturn();
        long songId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(put(songsUrl(releaseId) + "/" + songId)
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"Night Drive\", \"status\": \"RECORDING\" }"))
            .andExpect(status().isOk());

        mockMvc.perform(get(songsUrl(releaseId) + "/" + songId).with(as(alice)))
            .andExpect(jsonPath("$.durationSeconds").value(nullValue()))
            .andExpect(jsonPath("$.isrc").value(nullValue()))
            .andExpect(jsonPath("$.lyrics").value(nullValue()))
            .andExpect(jsonPath("$.explicit", is(false)));
    }

    @Test
    void updateStatus_changesOnlyTheStatus() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        long songId = createSong(alice, releaseId, "Intro");

        mockMvc.perform(patch(songsUrl(releaseId) + "/" + songId + "/status")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MIXING_MASTERING\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("MIXING_MASTERING")))
            .andExpect(jsonPath("$.title", is("Intro")));
    }

    // ---------- Access boundaries ----------

    @Test
    void releaseOfAnotherUser_isForbidden_forReadingAndAdding() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");

        mockMvc.perform(get(songsUrl(releaseId)).with(as(bob)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post(songsUrl(releaseId))
                .with(as(bob)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"Intruder\", \"status\": \"WRITING\" }"))
            .andExpect(status().isForbidden());

        assertThat(songRepository.count(), is(0L));
    }

    @Test
    void songOfAnotherOwnRelease_cannotBeReachedThroughTheWrongRelease() throws Exception {
        // Both releases belong to alice: the URL must still keep their tracklists apart
        long single = createRelease(alice, "Night Drive (Single)");
        long album = createRelease(alice, "Night Drive (Album)");
        long albumSong = createSong(alice, album, "Night Drive");

        mockMvc.perform(get(songsUrl(single) + "/" + albumSong).with(as(alice)))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete(songsUrl(single) + "/" + albumSong).with(as(alice)).with(csrf()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get(songsUrl(album)).with(as(alice)))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- Cascades ----------

    @Test
    void deletingTheRelease_deletesItsSongs() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        createSong(alice, releaseId, "Intro");
        createSong(alice, releaseId, "Outro");

        mockMvc.perform(delete(apiEndpoint + "/releases/" + releaseId).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(songRepository.count(), is(0L));
    }
}