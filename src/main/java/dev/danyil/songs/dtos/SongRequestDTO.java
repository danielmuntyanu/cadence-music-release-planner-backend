package dev.danyil.songs.dtos;

import dev.danyil.enums.SongStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create and full replace (PUT): every field is sent, null clears an optional one.
 * The song form always submits the whole song, so no field is "left unchanged".
 */
public record SongRequestDTO(

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must be at most 150 characters")
    String title,

    @Min(value = 1, message = "Duration must be at least 1 second")
    @Max(value = 10_800, message = "Duration must be at most 3 hours")
    Integer durationSeconds,

    // Accepted with or without hyphens and in any case; stored normalized. Blank clears it
    @Pattern(
        regexp = "^(|[A-Za-z]{2}-?[A-Za-z0-9]{3}-?\\d{2}-?\\d{5})$",
        message = "ISRC must look like US-ABC-26-00001")
    String isrc,

    @NotNull(message = "Status is required")
    SongStatus status,

    @Size(max = 20_000, message = "Lyrics must be at most 20000 characters")
    String lyrics,

    // Primitive: a missing value means "not explicit", never "unknown"
    boolean explicit
) {}