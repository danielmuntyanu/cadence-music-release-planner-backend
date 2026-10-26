package dev.danyil.releases.dtos;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;

/** PUT semantics: both dates are replaced together; null clears a date. */
public record ReleaseDatesRequestDTO(
    LocalDate releaseDate,
    LocalDate originalReleaseDate
) {

    @AssertTrue(message = "Original release date must be before the release date")
    public boolean isOriginalDateBeforeRelease() {
        return originalReleaseDate == null || releaseDate == null || originalReleaseDate.isBefore(releaseDate);
    }
}