package dev.danyil.storage.dtos;

import java.time.Instant;

/** A one-off link the browser follows to save a file under a readable name. */
public record DownloadLinkDTO(
    String url, 
    Instant expiresAt
) {}