package dev.danyil.releases;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.stream.Collectors;

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
import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import jakarta.servlet.http.Cookie;

/**
 * End-to-end flow through the real application and a real PostgreSQL database.
 * Covers what mocks cannot: the SQL built from Specifications, EXISTS-based artist
 * filtering, the ON DELETE CASCADE on artist links and the releaseCount formula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Seed data from data.sql would leak into counts and listings
    "spring.sql.init.mode=never"
})
class ReleaseIntegrationTest {

    private static final String PASSWORD = "password123!";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    private record TestUser(long id, Cookie[] cookies) {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    private String releasesUrl;
    private String artistsUrl;
    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() throws Exception {
        releasesUrl = apiEndpoint + "/releases";
        artistsUrl = apiEndpoint + "/artists";
        alice = registerAndLogin("alice");
        bob = registerAndLogin("bob");
    }

    @AfterEach
    void cleanDatabase() {
        // Children first: releases cascade to their artist links, then artists, then users.
        releaseRepository.deleteAll();
        artistRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Auth helpers (same as ArtistIntegrationTest) ----------

    private TestUser registerAndLogin(String username) throws Exception {
        MvcResult registration = mockMvc.perform(post(apiEndpoint + "/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "email": "%s@test.com",
                      "password": "%s",
                      "displayName": "%s"
                    }
                    """.formatted(username, username, PASSWORD, username)))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = JsonPath.read(registration.getResponse().getContentAsString(), "$.id");
        return new TestUser(id.longValue(), login(username));
    }

    private Cookie[] login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "%s", "password": "%s" }
                    """.formatted(username, PASSWORD)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

        Cookie[] cookies = result.getResponse().getCookies();
        assertThat("Login must set auth cookies", cookies, is(not(emptyArray())));
        return cookies;
    }

    private static RequestPostProcessor as(TestUser user) {
        return request -> {
            request.setCookies(user.cookies());
            return request;
        };
    }

    // ---------- Data helpers ----------

    private long createArtist(TestUser owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(artistsUrl)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"" + name + "\" }"))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String releaseJson(String name, String phase, String primaryGenre, String secondaryGenre, long... artistIds) {
        String ids = Arrays.stream(artistIds).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        String secondary = secondaryGenre == null ? "null" : "\"" + secondaryGenre + "\"";
        return """
            {
              "name": "%s",
              "type": "EP",
              "phase": "%s",
              "primaryGenre": "%s",
              "secondaryGenre": %s,
              "artistIds": [%s]
            }
            """.formatted(name, phase, primaryGenre, secondary, ids);
    }

    private long createReleaseFromJson(TestUser owner, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(releasesUrl)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createReleaseNamed(TestUser owner, String name) throws Exception {
        return createReleaseFromJson(owner, releaseJson(name, "PLANNING", "POP", null));
    }

    // ---------- Lifecycle and security ----------

    @Test
    void releaseLifecycle_createReadUpdatePhaseDelete_persistsEveryStep() throws Exception {
        long nova = createArtist(alice, "Nova");
        long ada = createArtist(alice, "Ada");
        long id = createReleaseFromJson(alice, releaseJson("Night Drive", "PLANNING", "POP", "ELECTRONIC", ada, nova));

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artists[*].name", contains("Ada", "Nova")))
            .andExpect(jsonPath("$.secondaryGenre", is("ELECTRONIC")));

        // Reordering keeps both links: UPDATE of positions, no unique-constraint violation
        mockMvc.perform(patch(releasesUrl + "/{id}", id)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"Night Drive (Deluxe)\", \"artistIds\": [%d, %d] }".formatted(nova, ada)))
            .andExpect(status().isOk());

        mockMvc.perform(patch(releasesUrl + "/{id}/phase", id)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"phase\": \"PROMO\" }"))
            .andExpect(status().isOk());

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Night Drive (Deluxe)")))
            .andExpect(jsonPath("$.phase", is("PROMO")))
            .andExpect(jsonPath("$.artists[*].name", contains("Nova", "Ada")));

        mockMvc.perform(delete(releasesUrl + "/{id}", id).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_withoutType_defaultsToSingle() throws Exception {
        long id = createReleaseFromJson(alice, """
            { "name": "Night Drive", "primaryGenre": "POP" }
            """);

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("SINGLE")))
            .andExpect(jsonPath("$.phase", is("PLANNING")));
    }

    @Test
    void request_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get(releasesUrl))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void releaseOfAnotherUser_cannotBeReadUpdatedOrDeleted() throws Exception {
        long id = createReleaseNamed(alice, "Night Drive");

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(bob)))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch(releasesUrl + "/{id}/phase", id)
                .with(as(bob))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"phase\": \"CATALOG\" }"))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete(releasesUrl + "/{id}", id).with(as(bob)).with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phase", is("PLANNING")));
    }

    @Test
    void create_withArtistOfAnotherUser_returns404AndSavesNothing() throws Exception {
        long bobsArtist = createArtist(bob, "Pulse");

        mockMvc.perform(post(releasesUrl)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(releaseJson("Night Drive", "PLANNING", "POP", null, bobsArtist)))
            .andExpect(status().isNotFound());

        assertThat(releaseRepository.count(), is(0L));
    }

    @Test
    void sameName_sameUserIgnoringCase_returns409_butAllowedForAnotherUser() throws Exception {
        createReleaseNamed(alice, "Night Drive");
        createReleaseNamed(bob, "Night Drive");

        mockMvc.perform(post(releasesUrl)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(releaseJson("NIGHT DRIVE", "PLANNING", "POP", null)))
            .andExpect(status().isConflict());

        assertThat(releaseRepository.count(), is(2L));
    }

    // ---------- Filtering ----------

    @Test
    void getAll_returnsOnlyCurrentUserReleases() throws Exception {
        createReleaseNamed(alice, "Night Drive");
        createReleaseNamed(alice, "Echoes");
        createReleaseNamed(bob, "Pulse");

        mockMvc.perform(get(releasesUrl).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Night Drive", "Echoes")));
    }

    @Test
    void filterByPhase_repeatedValues_matchAnyOfThem() throws Exception {
        createReleaseFromJson(alice, releaseJson("Planned", "PLANNING", "POP", null));
        createReleaseFromJson(alice, releaseJson("Teasing", "PRE_RELEASE", "POP", null));
        createReleaseFromJson(alice, releaseJson("Out Now", "PROMO", "POP", null));

        mockMvc.perform(get(releasesUrl).with(as(alice)).param("phase", "PRE_RELEASE", "PROMO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Teasing", "Out Now")));
    }

    @Test
    void filterByGenre_matchesPrimaryOrSecondaryGenre() throws Exception {
        createReleaseFromJson(alice, releaseJson("Pop First", "PLANNING", "POP", null));
        createReleaseFromJson(alice, releaseJson("Pop Second", "PLANNING", "ROCK", "POP"));
        createReleaseFromJson(alice, releaseJson("No Pop", "PLANNING", "JAZZ", null));

        mockMvc.perform(get(releasesUrl).with(as(alice)).param("genre", "POP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Pop First", "Pop Second")));
    }

    @Test
    void filterByArtist_returnsReleaseWithAllItsArtists() throws Exception {
        // EXISTS keeps the full artist list; a JOIN filter would return the release with Nova only
        long nova = createArtist(alice, "Nova");
        long ada = createArtist(alice, "Ada");
        createReleaseFromJson(alice, releaseJson("Duet", "PLANNING", "POP", null, nova, ada));
        createReleaseFromJson(alice, releaseJson("Ada Solo", "PLANNING", "POP", null, ada));

        mockMvc.perform(get(releasesUrl).with(as(alice)).param("artistId", String.valueOf(nova)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].name", is("Duet")))
            .andExpect(jsonPath("$.content[0].artists[*].name", contains("Nova", "Ada")));
    }

    @Test
    void differentFilters_combineWithAnd() throws Exception {
        long nova = createArtist(alice, "Nova");
        createReleaseFromJson(alice, releaseJson("Nova Pop", "PLANNING", "POP", null, nova));
        createReleaseFromJson(alice, releaseJson("Nova Rock", "PLANNING", "ROCK", null, nova));
        createReleaseFromJson(alice, releaseJson("Other Pop", "PLANNING", "POP", null));

        mockMvc.perform(get(releasesUrl).with(as(alice))
                .param("artistId", String.valueOf(nova))
                .param("genre", "POP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", contains("Nova Pop")));
    }

    @Test
    void filterBySearchText_isCaseInsensitivePartialMatch() throws Exception {
        createReleaseNamed(alice, "Night Drive");
        createReleaseNamed(alice, "Midnight Sun");
        createReleaseNamed(alice, "Echoes");

        mockMvc.perform(get(releasesUrl).with(as(alice)).param("q", "NIGHT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Night Drive", "Midnight Sun")));
    }

    @Test
    void filterByArtistOfAnotherUser_returnsNothing() throws Exception {
        long nova = createArtist(alice, "Nova");
        createReleaseFromJson(alice, releaseJson("Nova Pop", "PLANNING", "POP", null, nova));

        mockMvc.perform(get(releasesUrl).with(as(bob)).param("artistId", String.valueOf(nova)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------- Sorting ----------

    @Test
    void getAll_withoutSort_returnsNewestFirst() throws Exception {
        createReleaseNamed(alice, "First");
        createReleaseNamed(alice, "Second");

        mockMvc.perform(get(releasesUrl).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", contains("Second", "First")));
    }

    @Test
    void getAll_sortByName_isApplied() throws Exception {
        createReleaseNamed(alice, "Bravo");
        createReleaseNamed(alice, "Alpha");

        mockMvc.perform(get(releasesUrl).with(as(alice)).param("sort", "name,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].name", contains("Alpha", "Bravo")));
    }

    @Test
    void getAll_sortByForbiddenField_returns400() throws Exception {
        mockMvc.perform(get(releasesUrl).with(as(alice)).param("sort", "user.password,asc"))
            .andExpect(status().isBadRequest());
    }

    // ---------- Relation with artists ----------

    @Test
    void deletingArtist_keepsReleaseAndRemovesOnlyTheLink() throws Exception {
        long nova = createArtist(alice, "Nova");
        long ada = createArtist(alice, "Ada");
        long id = createReleaseFromJson(alice, releaseJson("Duet", "PLANNING", "POP", null, nova, ada));

        mockMvc.perform(delete(artistsUrl + "/{id}", nova).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(releasesUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artists[*].name", contains("Ada")));
    }

    @Test
    void artistReleaseCount_reflectsLinkedReleases() throws Exception {
        long nova = createArtist(alice, "Nova");
        createReleaseFromJson(alice, releaseJson("One", "PLANNING", "POP", null, nova));
        createReleaseFromJson(alice, releaseJson("Two", "PLANNING", "POP", null, nova));

        mockMvc.perform(get(artistsUrl + "/{id}", nova).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseCount", is(2)));
    }
}