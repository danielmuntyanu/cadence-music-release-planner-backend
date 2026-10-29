package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import dev.danyil.artists.ArtistRepository;
import dev.danyil.contracts.StorageService;
import dev.danyil.releases.ReleaseRepository;
import dev.danyil.support.TestImages;
import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import jakarta.servlet.http.Cookie;

/**
 * Song audio end to end: sign, upload straight to MinIO, confirm, play, download, delete.
 * The upload goes over real HTTP to storage, exactly as the browser will do it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "storage.auto-create-bucket=true",
    "storage.audio.max-bytes=1000"
})
class SongFileIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String MINIO_USER = "test-user";
    private static final String MINIO_PASSWORD = "test-password";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestImages.POSTGRES);

    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO = new GenericContainer<>(TestImages.MINIO)
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withCommand("server", "/data")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        POSTGRES.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        String url = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("storage.endpoint", () -> url);
        // The test plays the browser, so signed links must point at the same container
        registry.add("storage.public-endpoint", () -> url);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.access-key", () -> MINIO_USER);
        registry.add("storage.secret-key", () -> MINIO_PASSWORD);
        registry.add("storage.bucket", () -> "song-files-test");
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private record TestUser(long id, Cookie[] cookies) {}

    @Autowired private MockMvc mockMvc;
    @Autowired private StorageService storage;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private ReleaseRepository releaseRepository;
    @Autowired private SongFileRepository songFileRepository;

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
        // Releases cascade to songs, song files and artist links
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

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String songUrl(long releaseId, long songId) {
        return apiEndpoint + "/releases/" + releaseId + "/songs/" + songId;
    }

    /** A release with one song; returns {releaseId, songId}. */
    private long[] createSong(TestUser owner) throws Exception {
        long releaseId = idOf(mockMvc.perform(post(apiEndpoint + "/releases")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"Night Drive\", \"primaryGenre\": \"POP\" }"))
            .andExpect(status().isCreated()).andReturn());

        long songId = idOf(mockMvc.perform(post(apiEndpoint + "/releases/" + releaseId + "/songs")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"title\": \"Night Drive\", \"status\": \"MIXING_MASTERING\" }"))
            .andExpect(status().isCreated()).andReturn());

        return new long[] { releaseId, songId };
    }

    /** Step 1: asks the backend for a signed PUT; returns the response JSON. */
    private String startUpload(TestUser owner, long[] song, String fileName, int size) throws Exception {
        return mockMvc.perform(post(songUrl(song[0], song[1]) + "/files/uploads")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"fileName\": \"%s\", \"sizeBytes\": %d }".formatted(fileName, size)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    /** Step 2: what the browser does with the step 1 answer. */
    private static int putToStorage(String startResponse, byte[] audio) throws Exception {
        String url = JsonPath.read(startResponse, "$.uploadUrl");
        Map<String, String> headers = JsonPath.read(startResponse, "$.headers");

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofByteArray(audio));
        headers.forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** All three steps; returns the new file id. */
    private long upload(TestUser owner, long[] song, String fileName, byte[] audio) throws Exception {
        String started = startUpload(owner, song, fileName, audio.length);
        assertThat("Upload to storage", putToStorage(started, audio), is(200));
        String key = JsonPath.read(started, "$.key");

        return idOf(mockMvc.perform(post(songUrl(song[0], song[1]) + "/files")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"key\": \"%s\", \"fileName\": \"%s\" }".formatted(key, fileName)))
            .andExpect(status().isCreated())
            .andReturn());
    }

    // ---------- The whole path ----------

    @Test
    void uploadedAudio_canBeListedPlayedAndDownloaded() throws Exception {
        long[] song = createSong(alice);
        byte[] audio = { 82, 73, 70, 70, 1, 2, 3 };

        long fileId = upload(alice, song, "I Belong To You mix3.wav", audio);

        String listed = mockMvc.perform(get(songUrl(song[0], song[1]) + "/files").with(as(alice)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].displayName", is("I Belong To You mix3")))
            .andExpect(jsonPath("$[0].primary", is(true)))
            .andExpect(jsonPath("$[0].sizeBytes", is(audio.length)))
            .andReturn().getResponse().getContentAsString();

        // Playback: the signed URL serves the exact bytes
        String playUrl = JsonPath.read(listed, "$[0].url");
        HttpResponse<byte[]> played = HTTP.send(HttpRequest.newBuilder(URI.create(playUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(played.statusCode(), is(200));
        assertThat(played.body(), is(audio));

        // Download: same bytes, saved under the display name
        String link = mockMvc.perform(get(songUrl(song[0], song[1]) + "/files/" + fileId + "/download").with(as(alice)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        HttpResponse<byte[]> downloaded = HTTP.send(
            HttpRequest.newBuilder(URI.create(JsonPath.read(link, "$.url"))).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.statusCode(), is(200));
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElse(""),
            containsString("I%20Belong%20To%20You%20mix3.wav"));
        assertThat(downloaded.body(), is(audio));

        // The song in the tracklist knows its primary file
        mockMvc.perform(get(apiEndpoint + "/releases/" + song[0] + "/songs").with(as(alice)))
            .andExpect(jsonPath("$[0].primaryFileId", is((int) fileId)))
            .andExpect(jsonPath("$[0].fileCount", is(1)));
    }

    @Test
    void theHeartMoves_andDeletingAFileRemovesItsAudio() throws Exception {
        long[] song = createSong(alice);
        long demo = upload(alice, song, "demo.wav", new byte[] { 1 });
        long master = upload(alice, song, "master.wav", new byte[] { 2 });
        String masterKey = songFileRepository.findById(master).orElseThrow().getStorageKey();

        mockMvc.perform(put(songUrl(song[0], song[1]) + "/primary-file")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"fileId\": " + master + " }"))
            .andExpect(status().isOk())
            // Newest first: master, then demo
            .andExpect(jsonPath("$[0].primary", is(true)))
            .andExpect(jsonPath("$[1].primary", is(false)));

        // Deleting the primary file: ON DELETE SET NULL must let the row go
        mockMvc.perform(delete(songUrl(song[0], song[1]) + "/files/" + master).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(songUrl(song[0], song[1]) + "/files").with(as(alice)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is((int) demo)))
            .andExpect(jsonPath("$[0].primary", is(false)));

        assertThat(storage.describe(masterKey), is(Optional.empty()));
    }

    @Test
    void deletingTheRelease_removesTheAudioFromStorage() throws Exception {
        long[] song = createSong(alice);
        long fileId = upload(alice, song, "mix.wav", new byte[] { 1, 2 });
        String key = songFileRepository.findById(fileId).orElseThrow().getStorageKey();

        mockMvc.perform(delete(apiEndpoint + "/releases/" + song[0]).with(as(alice)).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(songFileRepository.count(), is(0L));
        assertThat(storage.describe(key), is(Optional.empty()));
    }

    // ---------- What must be refused ----------

    @Test
    void confirmingWithoutUploading_isRejected() throws Exception {
        long[] song = createSong(alice);
        String key = JsonPath.read(startUpload(alice, song, "mix.wav", 2), "$.key");

        mockMvc.perform(post(songUrl(song[0], song[1]) + "/files")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"key\": \"%s\", \"fileName\": \"mix.wav\" }".formatted(key)))
            .andExpect(status().isBadRequest());

        assertThat(songFileRepository.count(), is(0L));
    }

    @Test
    void anotherUser_cannotClaimSomeoneElsesUpload() throws Exception {
        long[] aliceSong = createSong(alice);
        long[] bobSong = createSong(bob);
        String started = startUpload(alice, aliceSong, "secret demo.wav", 2);
        putToStorage(started, new byte[] { 7, 7 });
        String aliceKey = JsonPath.read(started, "$.key");

        // Bob confirms Alice's key into his own song: he would get a signed link to her audio
        mockMvc.perform(post(songUrl(bobSong[0], bobSong[1]) + "/files")
                .with(as(bob)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"key\": \"%s\", \"fileName\": \"x.wav\" }".formatted(aliceKey)))
            .andExpect(status().isBadRequest());

        assertThat(songFileRepository.count(), is(0L));
    }

    @Test
    void theSameUpload_cannotBeConfirmedTwice() throws Exception {
        long[] song = createSong(alice);
        String started = startUpload(alice, song, "mix.wav", 2);
        putToStorage(started, new byte[] { 1, 2 });
        String body = "{ \"key\": \"%s\", \"fileName\": \"mix.wav\" }".formatted((String) JsonPath.read(started, "$.key"));

        mockMvc.perform(post(songUrl(song[0], song[1]) + "/files").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mockMvc.perform(post(songUrl(song[0], song[1]) + "/files").with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());

        assertThat(songFileRepository.count(), is(1L));
    }

    @Test
    void aFileOverTheLimit_isRefusedBeforeAnythingIsUploaded() throws Exception {
        long[] song = createSong(alice);

        mockMvc.perform(post(songUrl(song[0], song[1]) + "/files/uploads")
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"fileName\": \"huge.wav\", \"sizeBytes\": 1001 }"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void filesOfAnotherUsersSong_areForbidden() throws Exception {
        long[] aliceSong = createSong(alice);

        mockMvc.perform(get(songUrl(aliceSong[0], aliceSong[1]) + "/files").with(as(bob)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(songUrl(aliceSong[0], aliceSong[1]) + "/files/uploads")
                .with(as(bob)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"fileName\": \"x.wav\", \"sizeBytes\": 2 }"))
            .andExpect(status().isForbidden());
    }
}