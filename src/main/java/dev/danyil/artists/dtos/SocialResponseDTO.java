package dev.danyil.artists.dtos;

import dev.danyil.enums.SocialPlatform;
import lombok.Builder;

@Builder
public record SocialResponseDTO(
    SocialPlatform platform, 
    String url
) {}
