package dev.danyil.contracts;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import dev.danyil.storage.dtos.PresignedUpload;
import dev.danyil.storage.dtos.StoredObjectInfo;

/** Private object storage: MinIO locally, Cloudflare R2 in production. */
public interface StorageService {

    void upload(String key, byte[] content, String contentType);

    void delete(String key);

    /** Size and type of a stored object, or empty when it does not exist. */
    Optional<StoredObjectInfo> describe(String key);

    /** Cached short-lived URL for showing or playing a private file; null for a null key. */
    String signedReadUrl(String key);

    URL presignGet(String key, Duration ttl);

    /**
     * Signs a PUT for exactly this content type and length: storage rejects a body that
     * differs, so a client cannot use the link to upload a larger or different file.
     */
    PresignedUpload presignPut(String key, String contentType, long contentLength, Duration ttl);

    /**
     * Signed GET that makes the browser save the file under this name instead of playing
     * or showing it. Not cached: every link carries its own file name.
     */
    String presignDownload(String key, String filename, Duration ttl);
}