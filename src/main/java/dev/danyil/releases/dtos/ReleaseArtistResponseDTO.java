package dev.danyil.releases.dtos;

import lombok.Builder;

@Builder
public record ReleaseArtistResponseDTO(
    Long id,
    String name,
    String avatarUrl
) {}