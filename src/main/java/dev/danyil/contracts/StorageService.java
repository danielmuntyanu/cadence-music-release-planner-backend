package dev.danyil.contracts;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import dev.danyil.storage.dtos.StoredObjectInfo;

/**
 * Private object storage behind an S3-compatible API (MinIO locally, Cloudflare R2 in production).
 * Nothing is publicly readable: files are reached only through short-lived signed URLs,
 * which the API hands out after checking ownership. The database stores keys, never URLs.
 */
public interface StorageService {

    void upload(String key, byte[] content, String contentType);

    /** Idempotent: deleting a missing key is not an error. */
    void delete(String key);

    /** Size and type of a stored object, or empty if it does not exist. */
    Optional<StoredObjectInfo> describe(String key);

    /**
     * Read URL for showing a file in the UI (img src, audio src).
     * Reused while comfortably valid, so browsers can cache the file between API calls.
     * Returns null for a null key, so optional images map without extra checks.
     */
    String signedReadUrl(String key);

    /** One-off read URL with an explicit lifetime. */
    URL presignGet(String key, Duration ttl);

    /** Temporary upload URL, so the browser can send large files directly to storage. */
    URL presignPut(String key, String contentType, Duration ttl);
}