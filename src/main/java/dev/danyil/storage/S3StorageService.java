package dev.danyil.storage;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.danyil.contracts.StorageService;
import dev.danyil.storage.dtos.PresignedUpload;
import dev.danyil.storage.dtos.StoredObjectInfo;
import dev.danyil.storage.exceptions.StorageException;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    // Above this size, expired entries are dropped so the cache cannot grow without bound
    private static final int CACHE_PURGE_THRESHOLD = 10_000;

    // Signed headers the browser sets on its own and does not allow scripts to set
    private static final Set<String> BROWSER_MANAGED_HEADERS = Set.of("host", "content-length");

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final Clock clock;

    /** A signed URL and the moment it stops being reused (well before it actually expires). */
    private record CachedUrl(String url, Instant refreshAt) {}

    private final Map<String, CachedUrl> signedUrls = new ConcurrentHashMap<>();

    @Override
    public void upload(String key, byte[] content, String contentType) {
        try {
            s3.putObject(
                request -> request.bucket(properties.bucket()).key(key).contentType(contentType),
                RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Could not upload " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(request -> request.bucket(properties.bucket()).key(key));
            signedUrls.remove(key);
        } catch (SdkException e) {
            throw new StorageException("Could not delete " + key, e);
        }
    }

    @Override
    public Optional<StoredObjectInfo> describe(String key) {
        try {
            HeadObjectResponse head = s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObjectInfo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HEAD responses have no body, so some servers report a missing key as a plain 404
            if (e.statusCode() == 404) return Optional.empty();
            throw new StorageException("Could not read " + key, e);
        } catch (SdkException e) {
            throw new StorageException("Could not read " + key, e);
        }
    }

    @Override
    public String signedReadUrl(String key) {
        if (key == null) return null;

        Instant now = clock.instant();
        CachedUrl cached = signedUrls.get(key);
        if (cached != null && now.isBefore(cached.refreshAt())) return cached.url();

        // A new signature is issued when only a quarter of the lifetime is left,
        // so a URL handed to a page always has a useful amount of validity remaining
        Duration ttl = properties.signedUrlTtl();
        String url = presignGet(key, ttl).toString();
        signedUrls.put(key, new CachedUrl(url, now.plus(ttl.minus(ttl.dividedBy(4)))));

        if (signedUrls.size() > CACHE_PURGE_THRESHOLD) {
            signedUrls.values().removeIf(entry -> !now.isBefore(entry.refreshAt()));
        }
        return url;
    }

    @Override
    public URL presignGet(String key, Duration ttl) {
        return presigner.presignGetObject(request -> request
                .signatureDuration(ttl)
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)))
            .url();
    }

    @Override
    public PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
            .signatureDuration(ttl)
            .putObjectRequest(put -> put
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)));

        return new PresignedUpload(presigned.url().toString(), presigned.expiration(), headersForBrowser(presigned));
    }

    @Override
    public String presignDownload(String key, String filename, Duration ttl) {
        return presigner.presignGetObject(request -> request
                .signatureDuration(ttl)
                .getObjectRequest(get -> get
                    .bucket(properties.bucket())
                    .key(key)
                    .responseContentDisposition(attachment(filename))))
            .url()
            .toString();
    }

    // HELPERS

    /**
     * Every header that is part of the signature must reach storage unchanged. The browser
     * adds Host and Content-Length itself; the rest (Content-Type at least) the page must send.
     */
    private static Map<String, String> headersForBrowser(PresignedPutObjectRequest presigned) {
        return presigned.signedHeaders().entrySet().stream()
            .filter(header -> !BROWSER_MANAGED_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT)))
            .collect(Collectors.toMap(Map.Entry::getKey, header -> String.join(",", header.getValue())));
    }

    /**
     * Two file names: a plain ASCII one for old clients and an RFC 5987 one for the rest,
     * so "Lía Montes - Neon Siesta.png" keeps its accents in every modern browser.
     */
    static String attachment(String filename) {
        String ascii = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}