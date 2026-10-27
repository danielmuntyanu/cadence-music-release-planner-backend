package dev.danyil.storage.dtos;

import java.time.Instant;
import java.util.Map;

/**
 * A signed PUT the browser uses to upload a file straight to storage.
 * headers: what the browser must send exactly as signed, or storage rejects the upload.
 */
public record PresignedUpload(
    String url, 
    Instant expiresAt, 
    Map<String, String> headers
) {}