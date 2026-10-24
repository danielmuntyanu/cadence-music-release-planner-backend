package dev.danyil.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
 * Image uploads end to end: real security, PostgreSQL and MinIO.
 * Proves that signed URLs from the API actually serve the image, that foreign requests
 * store nothing, and that replaced files are physically removed from storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
class MediaUploadIntegrationTest {

    private static final String PASSWORD = "password123!";
    private static final String MINIO_USER = "test-user";
    private static final String MINIO_PASSWORD = "test-password";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestImages.POSTGRES);

    // Closed by Testcontainers (Ryuk) when the JVM exits; the analyzer cannot follow the with* chain
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
        registry.add("storage.public-endpoint", () -> url);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.access-key", () -> MINIO_USER);
        registry.add("storage.secret-key", () -> MINIO_PASSWORD);
        registry.add("storage.bucket", () -> "test-files");
        registry.add("storage.auto-create-bucket", () -> "true");
    }

    private record TestUser(long id, Cookie[] cookies) {}

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired private MockMvc mockMvc;
    @Autowired private StorageService storage;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private ReleaseRepository releaseRepository;

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

    private static MockMultipartFile png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "picture.png", "image/png", output.toByteArray());
    }

    private long createArtist(TestUser owner, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(apiEndpoint + "/artists")
                .with(as(owner)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"name\": \"" + name + "\" }"))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
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

    private String upload(String url, TestUser user) throws Exception {
        MvcResult result = mockMvc.perform(multipart(HttpMethod.PUT, url).file(png()).with(as(user)).with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static HttpResponse<byte[]> download(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    // ---------- Artist ----------

    @Test
    void artistAvatar_upload_returnsSignedUrlThatServesAJpeg() throws Exception {
        long artistId = createArtist(alice, "Nova");

        String body = upload(apiEndpoint + "/artists/" + artistId + "/avatar", alice);
        String avatarUrl = JsonPath.read(body, "$.avatarUrl");

        assertThat(avatarUrl, containsString("X-Amz-Signature"));
        HttpResponse<byte[]> image = download(avatarUrl);
        assertThat(image.statusCode(), is(200));
        assertThat(image.headers().firstValue("Content-Type"), is(Optional.of("image/jpeg")));
    }

    @Test
    void artistAvatar_otherUser_isForbidden_andStoresNothing() throws Exception {
        long artistId = createArtist(alice, "Nova");

        mockMvc.perform(multipart(HttpMethod.PUT, apiEndpoint + "/artists/" + artistId + "/avatar")
                .file(png()).with(as(bob)).with(csrf()))
            .andExpect(status().isForbidden());

        assertThat(artistRepository.findById(artistId).orElseThrow().getAvatarKey(), is(nullValue()));
    }

    @Test
    void artistAvatar_replacedByLink_deletesTheStoredFile() throws Exception {
        long artistId = createArtist(alice, "Nova");
        upload(apiEndpoint + "/artists/" + artistId + "/avatar", alice);
        String storedKey = artistRepository.findById(artistId).orElseThrow().getAvatarKey();

        mockMvc.perform(patch(apiEndpoint + "/artists/" + artistId)
                .with(as(alice)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"avatarUrl\": \"https://example.com/nova.png\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl", is("https://example.com/nova.png")));

        assertThat(storage.describe(storedKey), is(Optional.empty()));
    }

    @Test
    void notAnImage_isRejected() throws Exception {
        long artistId = createArtist(alice, "Nova");
        MockMultipartFile text = new MockMultipartFile("file", "fake.png", "image/png", "not a picture".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, apiEndpoint + "/artists/" + artistId + "/banner")
                .file(text).with(as(alice)).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    // ---------- Release ----------

    @Test
    void releaseArtwork_uploadThenRemove_cleansUpTheFile() throws Exception {
        long releaseId = createRelease(alice, "Night Drive");
        upload(apiEndpoint + "/releases/" + releaseId + "/artwork", alice);
        String storedKey = releaseRepository.findById(releaseId).orElseThrow().getArtworkKey();
        assertThat(storedKey, is(notNullValue()));

        mockMvc.perform(delete(apiEndpoint + "/releases/" + releaseId + "/artwork").with(as(alice)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artworkUrl").value(nullValue()));

        assertThat(storage.describe(storedKey), is(Optional.empty()));
    }

    // ---------- User ----------

    @Test
    void userAvatar_upload_showsInTheCurrentUser() throws Exception {
        upload(apiEndpoint + "/users/me/avatar", alice);

        MvcResult me = mockMvc.perform(get(apiEndpoint + "/auth/me").with(as(alice)))
            .andExpect(status().isOk())
            .andReturn();
        String avatarUrl = JsonPath.read(me.getResponse().getContentAsString(), "$.avatarUrl");

        assertThat(download(avatarUrl).statusCode(), is(200));
    }
}