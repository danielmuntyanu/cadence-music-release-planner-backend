package dev.danyil.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import dev.danyil.contracts.StorageService;
import dev.danyil.storage.dtos.PresignedUpload;
import dev.danyil.storage.dtos.StoredObjectInfo;
import dev.danyil.support.TestImages;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3StorageService against a real MinIO. The key guarantee: no file is readable
 * without a signature, so privacy does not depend on keys being hard to guess.
 */
class S3StorageServiceIntegrationTest {

    private static final String ACCESS_KEY = "test-user";
    private static final String SECRET_KEY = "test-password";
    private static final String BUCKET = "test-files";
    private static final Duration SIGNED_URL_TTL = Duration.ofHours(1);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(5);

    // Upstream MinIO no longer publishes images; pgsty/minio is a community rebuild from the same source
    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO = new GenericContainer<>(TestImages.MINIO)
        .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
        .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
        .withCommand("server", "/data")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        MINIO.start();
    }

    /** A clock the test can move forward, to exercise signed URL reuse and refresh. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T12:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String baseUrl;
    private static S3Client s3;
    private static StorageProperties properties;

    private MutableClock clock;
    private S3Presigner presigner;
    private StorageService storage;

    @BeforeAll
    static void createBucket() {
        baseUrl = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        properties = new StorageProperties(
            URI.create(baseUrl), URI.create(baseUrl), "us-east-1", ACCESS_KEY, SECRET_KEY,
            BUCKET, SIGNED_URL_TTL, DOWNLOAD_URL_TTL, true);

        s3 = new StorageConfig().s3Client(properties);
        new StorageBucketInitializer(s3, properties).run(null);
    }

    @BeforeEach
    void setUp() {
        // Fresh service per test: the signed URL cache must not leak between tests
        clock = new MutableClock();
        presigner = spy(new StorageConfig().s3Presigner(properties));
        storage = new S3StorageService(s3, presigner, properties, clock);
    }

    // ---------- Helpers ----------

    private static String uniqueKey(String name) {
        return "tests/" + UUID.randomUUID() + "/" + name;
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Uploads like the browser will: to the signed URL, with exactly the headers it was signed with. */
    private static HttpResponse<String> put(PresignedUpload upload, byte[] body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.url()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        upload.headers().forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------- Privacy ----------

    @Test
    void storedFile_isDeniedWithoutSignature() throws Exception {
        String key = uniqueKey("cover.webp");
        storage.upload(key, "unreleased cover".getBytes(StandardCharsets.UTF_8), "image/webp");

        assertThat(get(baseUrl + "/" + BUCKET + "/" + key).statusCode(), is(403));
    }

    @Test
    void storedFile_isReadableThroughSignedUrl() throws Exception {
        String key = uniqueKey("cover.webp");
        storage.upload(key, "unreleased cover".getBytes(StandardCharsets.UTF_8), "image/webp");

        HttpResponse<byte[]> response = get(storage.signedReadUrl(key));

        assertThat(response.statusCode(), is(200));
        assertThat(text(response), is("unreleased cover"));
    }

    // ---------- Signed URL cache ----------

    @Test
    void signedReadUrl_nullKey_isNull() {
        assertThat(storage.signedReadUrl(null), is(nullValue()));
    }

    @Test
    void signedReadUrl_isReusedWhileComfortablyValid() {
        String key = uniqueKey("avatar.webp");

        String first = storage.signedReadUrl(key);
        clock.advance(Duration.ofMinutes(44));
        String second = storage.signedReadUrl(key);

        // Same URL lets the browser serve the image from its cache
        assertThat(second, is(first));
        verify(presigner, times(1)).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void signedReadUrl_isResignedWhenAQuarterOfItsLifetimeIsLeft() {
        String key = uniqueKey("avatar.webp");
        storage.signedReadUrl(key);
        clearInvocations(presigner);

        clock.advance(Duration.ofMinutes(45));
        storage.signedReadUrl(key);

        verify(presigner, times(1)).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void delete_dropsTheCachedUrl() {
        String key = uniqueKey("old.webp");
        storage.upload(key, new byte[] { 1 }, "image/webp");
        storage.signedReadUrl(key);
        clearInvocations(presigner);

        storage.delete(key);
        storage.signedReadUrl(key);

        verify(presigner, times(1)).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void signedReadUrl_differentKeys_getDifferentUrls() {
        assertThat(storage.signedReadUrl(uniqueKey("a.webp")), is(not(storage.signedReadUrl(uniqueKey("b.webp")))));
    }

    // ---------- Upload, describe, delete ----------

    @Test
    void upload_storesContentAndType() {
        String key = uniqueKey("cover.webp");

        storage.upload(key, new byte[] { 1, 2, 3 }, "image/webp");

        assertThat(storage.describe(key), is(Optional.of(new StoredObjectInfo(3, "image/webp"))));
    }

    @Test
    void delete_removesTheObject_andIgnoresMissingKeys() {
        String key = uniqueKey("old.webp");
        storage.upload(key, new byte[] { 1 }, "image/webp");

        storage.delete(key);
        storage.delete(key);

        assertThat(storage.describe(key), is(Optional.empty()));
    }

    @Test
    void describe_missingKey_isEmpty() {
        assertThat(storage.describe(uniqueKey("nothing.wav")), is(Optional.empty()));
    }

    // ---------- Direct uploads (presigned PUT) ----------

    @Test
    void presignPut_letsAClientUploadDirectly() throws Exception {
        String key = uniqueKey("mix3.wav");
        byte[] audio = { 9, 9 };
        PresignedUpload upload = storage.presignPut(key, "audio/wav", audio.length, Duration.ofMinutes(5));

        HttpResponse<String> response = put(upload, audio);

        // The body explains a failure: MinIO answers with an XML error such as SignatureDoesNotMatch
        assertThat(response.body(), response.statusCode(), is(200));
        assertThat(storage.describe(key), is(Optional.of(new StoredObjectInfo(2, "audio/wav"))));
    }

    @Test
    void presignPut_tellsTheBrowserWhichHeadersToSend() {
        PresignedUpload upload = storage.presignPut(uniqueKey("mix3.wav"), "audio/wav", 2, Duration.ofMinutes(5));

        boolean sendsContentType = upload.headers().entrySet().stream()
            .anyMatch(header -> header.getKey().equalsIgnoreCase("content-type") && header.getValue().equals("audio/wav"));
        boolean leavesBrowserHeadersOut = upload.headers().keySet().stream()
            .noneMatch(name -> name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length"));

        assertThat(sendsContentType, is(true));
        assertThat(leavesBrowserHeadersOut, is(true));
        assertThat(upload.expiresAt(), is(not(nullValue())));
    }

    @Test
    void presignPut_rejectsALargerFileThanSigned() throws Exception {
        // The size limit lives in the signature: a link for 2 bytes cannot carry 3
        String key = uniqueKey("mix3.wav");
        PresignedUpload upload = storage.presignPut(key, "audio/wav", 2, Duration.ofMinutes(5));

        HttpResponse<String> response = put(upload, new byte[] { 9, 9, 9 });

        assertThat(response.statusCode(), is(not(200)));
        assertThat(storage.describe(key), is(Optional.empty()));
    }

    @Test
    void presignPut_rejectsADifferentContentType() throws Exception {
        String key = uniqueKey("mix3.wav");
        PresignedUpload upload = storage.presignPut(key, "audio/wav", 2, Duration.ofMinutes(5));

        HttpResponse<String> response = HTTP.send(
            HttpRequest.newBuilder(URI.create(upload.url()))
                .header("Content-Type", "text/html")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[] { 9, 9 }))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(not(200)));
        assertThat(storage.describe(key), is(Optional.empty()));
    }

    // ---------- Downloads ----------

    @Test
    void presignDownload_savesTheFileUnderTheGivenName() throws Exception {
        String key = uniqueKey("9fc1b67e.wav");
        storage.upload(key, new byte[] { 4, 2 }, "audio/wav");

        HttpResponse<byte[]> response = get(storage.presignDownload(key, "Lía Montes - mix 3.wav", DOWNLOAD_URL_TTL));

        assertThat(response.statusCode(), is(200));
        String disposition = response.headers().firstValue("Content-Disposition").orElse("");
        assertThat(disposition, containsString("attachment"));
        // ASCII fallback for old clients, UTF-8 name for the rest
        assertThat(disposition, containsString("filename=\"L_a Montes - mix 3.wav\""));
        assertThat(disposition, containsString("filename*=UTF-8''L%C3%ADa%20Montes%20-%20mix%203.wav"));
        assertThat(response.body(), is(new byte[] { 4, 2 }));
    }

    @Test
    void presignDownload_isNotCached() {
        String key = uniqueKey("mix.wav");

        String first = storage.presignDownload(key, "Mix A.wav", DOWNLOAD_URL_TTL);
        String second = storage.presignDownload(key, "Mix B.wav", DOWNLOAD_URL_TTL);

        // Each link carries its own file name, so links for the same file must differ
        assertThat(second, is(not(first)));
    }
}