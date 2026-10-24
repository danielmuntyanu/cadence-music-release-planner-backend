package dev.danyil.storage;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import dev.danyil.contracts.StorageService;
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

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    // Above this size, expired entries are dropped so the cache cannot grow without bound
    private static final int CACHE_PURGE_THRESHOLD = 10_000;

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
    public URL presignPut(String key, String contentType, Duration ttl) {
        return presigner.presignPutObject(request -> request
                .signatureDuration(ttl)
                .putObjectRequest(put -> put.bucket(properties.bucket()).key(key).contentType(contentType)))
            .url();
    }
}