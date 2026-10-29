package dev.danyil.songs.dtos;

import dev.danyil.enums.SongStatus;
import jakarta.validation.constraints.NotNull;

public record SongStatusUpdateRequestDTO(
    @NotNull(message = "Status is required")
    SongStatus status
) {}