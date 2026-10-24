package dev.danyil.releases.dtos;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReleaseCreateRequestDTO(

    @NotBlank(message = "Name is required")
    @Size(max = 128, message = "Name must be at most 150 characters")
    String name,

    // Optional: new releases without a type will be marked as SINGLE
    ReleaseType type,

    // Optional: new releases start in PLANNING
    ReleasePhase phase,

    @Size(max = 1024, message = "Notes must be at most 1024 characters")
    String notes,

    @NotNull(message = "Primary genre is required")
    Genre primaryGenre,

    Genre secondaryGenre,

    LocalDate releaseDate,

    LocalDate originalReleaseDate,

    // Order matters: the first id becomes the primary artist
    @Size(max = 20, message = "A release can have at most 20 artists")
    List<@NotNull Long> artistIds
) {

    @AssertTrue(message = "Secondary genre must differ from the primary genre")
    public boolean isGenresDistinct() {
        return secondaryGenre == null || secondaryGenre != primaryGenre;
    }

    @AssertTrue(message = "An artist can only be added once")
    public boolean isArtistIdsUnique() {
        return artistIds == null || new HashSet<>(artistIds).size() == artistIds.size();
    }

    @AssertTrue(message = "Original release date must be before the release date")
    public boolean isOriginalDateBeforeRelease() {
        return originalReleaseDate == null || releaseDate == null || originalReleaseDate.isBefore(releaseDate);
    }
}