package dev.danyil.releases.dtos;

import dev.danyil.enums.Genre;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** PUT semantics: both genres are replaced together. */
public record ReleaseGenresRequestDTO(

    @NotNull(message = "Primary genre is required")
    Genre primaryGenre,

    // null removes the secondary genre
    Genre secondaryGenre
) {

    @AssertTrue(message = "Secondary genre must differ from the primary genre")
    public boolean isGenresDistinct() {
        return secondaryGenre == null || secondaryGenre != primaryGenre;
    }
}