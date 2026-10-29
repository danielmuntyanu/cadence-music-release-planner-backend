package dev.danyil.releases.dtos;

import java.util.HashSet;
import java.util.List;

import dev.danyil.enums.ReleaseType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null keeps the current value; "" clears an optional text field. */
public record ReleaseUpdateRequestDTO(

    @Size(max = 150, message = "Name must be at most 150 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    String name,

    ReleaseType type,

    @Size(max = 10_000, message = "Notes must be at most 10000 characters")
    String notes,

    // null keeps the current artists; [] removes all of them
    @Size(max = 20, message = "A release can have at most 20 artists")
    List<@NotNull Long> artistIds
) {

    @AssertTrue(message = "An artist can only be added once")
    public boolean isArtistIdsUnique() {
        return artistIds == null || new HashSet<>(artistIds).size() == artistIds.size();
    }
}