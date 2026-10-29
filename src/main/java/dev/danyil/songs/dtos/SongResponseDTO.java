package dev.danyil.songs.dtos;

import java.time.Instant;

import dev.danyil.enums.SongStatus;
import lombok.Builder;

@Builder
public record SongResponseDTO(
    Long id,
    // 1-based, as shown to people; the 0-based position stays internal
    int trackNumber,
    String title,
    Integer durationSeconds,
    String isrc,
    SongStatus status,
    String lyrics,
    boolean explicit,
    // The heart: lets the tracklist play a song without loading its files
    Long primaryFileId,
    int fileCount,
    Instant createdAt
) {}