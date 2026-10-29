package dev.danyil.releases.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import lombok.Builder;

@Builder
public record ReleaseResponseDTO(
    Long id,
    String name,
    ReleaseType type,
    ReleasePhase phase,
    String artworkUrl,
    String notes,
    Genre primaryGenre,
    Genre secondaryGenre,
    LocalDate releaseDate,
    LocalDate originalReleaseDate,
    // Ordered by position: the first artist is the primary one
    List<ReleaseArtistResponseDTO> artists,
    Instant createdAt,
    // Derived from the songs on every read, never stored
    int songCount,
    int doneSongCount,
    // Average workflow progress of the songs, 0..100
    int progressPercent,
    // True when at least one song is explicit, as distributors mark releases
    boolean explicit
) {}