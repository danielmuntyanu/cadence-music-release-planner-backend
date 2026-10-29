package dev.danyil.artists.dtos;

import java.util.List;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArtistUpdateRequestDTO(

    @Size(max = 50, message = "Name must be at most 50 characters")
    @Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
    String name,

    @URL(message = "Avatar must be a valid URL")
    @Size(max = 512)
    String avatarUrl,

    @URL(message = "Banner must be a valid URL")
    @Size(max = 512)
    String bannerUrl,

    @Size(max = 512, message = "Description must be at most 512 characters")
    String description,

    @Size(max = 50, message = "Genre must be at most 50 characters")
    String genre,

    @Size(max = 20, message = "An artist can have at most 20 social links")
    List<@Valid SocialRequestDTO> socials
) {}
