package dev.danyil.artists;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyArray;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import jakarta.servlet.http.Cookie;

/**
 * End-to-end flow through the real application: security filter chain,
 * controller, validation, service, JPA and a real PostgreSQL database.
 *
 * Users are registered and logged in through the real endpoints, and every
 * request carries the auth cookies returned by the login, exactly like a browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Seed data from data.sql would leak into counts and listings
    "spring.sql.init.mode=never"
})
class ArtistIntegrationTest {

    private static final String PASSWORD = "password123!";

    // One container for the whole test class, started before the Spring context.
    // @ServiceConnection points the datasource at it, no manual URL/user/password.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    /** A registered and logged-in user: database id plus the auth cookies from the login response. */
    private record TestUser(long id, Cookie[] cookies) {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    private String artistsUrl;
    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() throws Exception {
        artistsUrl = apiEndpoint + "/artists";
        alice = registerAndLogin("alice");
        bob = registerAndLogin("bob");
    }

    @AfterEach
    void cleanDatabase() {
        // Children first: artists and profiles reference users through foreign keys.
        artistRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- Helpers ----------

    /**
     * Registers a user through the real registration endpoint, so every required column,
     * default value and role is filled exactly as in production, then logs in.
     */
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

    /** Logs in through the real auth endpoint and returns the cookies it sets. */
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

    /** Sends the user's auth cookies with the request, like a browser does. */
    private static RequestPostProcessor as(TestUser user) {
        return request -> {
            request.setCookies(user.cookies());
            return request;
        };
    }

    private long createArtistFromJson(TestUser owner, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(artistsUrl)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createArtistNamed(TestUser owner, String name) throws Exception {
        return createArtistFromJson(owner, "{ \"name\": \"" + name + "\" }");
    }

    // ---------- Tests ----------

    @Test
    void artistLifecycle_createReadUpdateDelete_persistsEveryStep() throws Exception {
        long id = createArtistFromJson(alice, """
            {
              "name": "Nova",
              "genre": "Synthwave",
              "socials": [
                { "platform": "SPOTIFY", "url": "https://open.spotify.com/artist/nova" }
              ]
            }
            """);

        mockMvc.perform(get(artistsUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Nova")))
            .andExpect(jsonPath("$.socials", hasSize(1)));

        // Replacing the element collection is a delete + insert in artist_socials.
        mockMvc.perform(patch(artistsUrl + "/{id}", id)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "genre": "Darkwave",
                      "socials": [
                        { "platform": "INSTAGRAM", "url": "https://instagram.com/nova" },
                        { "platform": "BANDCAMP", "url": "https://nova.bandcamp.com" }
                      ]
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get(artistsUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Nova")))
            .andExpect(jsonPath("$.genre", is("Darkwave")))
            .andExpect(jsonPath("$.socials", hasSize(2)));

        mockMvc.perform(delete(artistsUrl + "/{id}", id).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(artistsUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isNotFound());
    }

    @Test
    void request_withoutAuthentication_isRejected() throws Exception {
        mockMvc.perform(get(artistsUrl))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returnsOnlyCurrentUserArtists() throws Exception {
        createArtistNamed(alice, "Nova");
        createArtistNamed(alice, "Echo");
        createArtistNamed(bob, "Pulse");

        mockMvc.perform(get(artistsUrl).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void getAll_respectsPageSize() throws Exception {
        createArtistNamed(alice, "Nova");
        createArtistNamed(alice, "Echo");
        createArtistNamed(alice, "Pulse");

        mockMvc.perform(get(artistsUrl).with(as(alice)).param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void sameName_forDifferentUsers_isAllowed() throws Exception {
        // Checks the composite unique constraint (user_id, name) in the real schema.
        createArtistNamed(alice, "Nova");
        createArtistNamed(bob, "Nova");

        assertThat(artistRepository.count(), is(2L));
    }

    @Test
    void sameName_forSameUserIgnoringCase_returns409() throws Exception {
        createArtistNamed(alice, "Nova");

        mockMvc.perform(post(artistsUrl)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "NOVA" }
                    """))
            .andExpect(status().isConflict());

        assertThat(artistRepository.count(), is(1L));
    }

    @Test
    void artistOfAnotherUser_cannotBeReadUpdatedOrDeleted() throws Exception {
        long id = createArtistNamed(alice, "Nova");

        mockMvc.perform(get(artistsUrl + "/{id}", id).with(as(bob)))
            .andExpect(status().isForbidden());

        mockMvc.perform(patch(artistsUrl + "/{id}", id)
                .with(as(bob))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Hacked" }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete(artistsUrl + "/{id}", id).with(as(bob)).with(csrf()))
            .andExpect(status().isForbidden());

        // The artist is untouched.
        mockMvc.perform(get(artistsUrl + "/{id}", id).with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Nova")));
    }

    @Test
    void invalidRequest_doesNotReachDatabase() throws Exception {
        mockMvc.perform(post(artistsUrl)
                .with(as(alice))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Nova", "avatarUrl": "not a url" }
                    """))
            .andExpect(status().isBadRequest());

        assertThat(artistRepository.count(), is(0L));
    }
}