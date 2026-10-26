package dev.danyil.songs.dtos;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** The full new order of the release's songs, first track first. */
public record SongOrderRequestDTO(
    @NotNull(message = "Song order is required")
    List<@NotNull Long> songIds
) {}
