package dev.danyil.songs.dtos;

import java.time.Instant;

import lombok.Builder;

@Builder
public record SongFileResponseDTO(
    Long id,
    String displayName,
    String originalFilename,
    String contentType,
    long sizeBytes,
    Instant uploadedAt,
    boolean primary,
    // Short-lived signed URL for playback; a fresh one comes with every response
    String url
) {}