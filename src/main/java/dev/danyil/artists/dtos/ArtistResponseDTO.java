package dev.danyil.artists.dtos;

import java.util.List;

import lombok.Builder;

@Builder 
public record ArtistResponseDTO(
    Long id,
    String name,
    String avatarUrl,
    String bannerUrl,
    String description,
    String genre,
    List<SocialResponseDTO> socials,
    Long releaseCount
) {}
