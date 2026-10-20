package dev.danyil.releases.dtos;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.hibernate.validator.constraints.URL;

import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleaseType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null keeps the current value; "" clears an optional text field. */
public record ReleaseUpdateRequestDTO(

    @Size(max = 128, message = "Name must be at most 150 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    String name,

    ReleaseType type,

    @URL(message = "Artwork must be a valid URL")
    @Size(max = 512)
    String artworkUrl,

    @Size(max = 1024, message = "Notes must be at most 1024 characters")
    String notes,

    Genre primaryGenre,

    Genre secondaryGenre,

    LocalDate releaseDate,

    LocalDate originalReleaseDate,

    // null keeps the current artists; [] removes all of them
    @Size(max = 20, message = "A release can have at most 20 artists")
    List<@NotNull Long> artistIds
) {

    @AssertTrue(message = "An artist can only be added once")
    public boolean isArtistIdsUnique() {
        return artistIds == null || new HashSet<>(artistIds).size() == artistIds.size();
    }
}