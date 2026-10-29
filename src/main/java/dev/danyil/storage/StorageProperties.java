package dev.danyil.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Storage connection and link lifetimes; the same keys work for MinIO and Cloudflare R2. */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
    // Where the backend talks to storage
    URI endpoint,
    // Where the browser opens signed links
    URI publicEndpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucket,
    // Links for showing and playing files: cached and reused
    Duration signedUrlTtl,
    // Links for saving a file: created per click, never cached
    Duration downloadUrlTtl,
    boolean autoCreateBucket
) {}