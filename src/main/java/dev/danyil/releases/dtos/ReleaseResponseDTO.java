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
    Instant createdAt
) {}