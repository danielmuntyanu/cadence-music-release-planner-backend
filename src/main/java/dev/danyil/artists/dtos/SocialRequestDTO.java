package dev.danyil.artists.dtos;

import org.hibernate.validator.constraints.URL;

import dev.danyil.enums.SocialPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialRequestDTO(

    @NotNull(message = "Platform is required")
    SocialPlatform platform,

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    @Size(max = 512, message = "URL must be at most 512 characters")
    String url
) {}
