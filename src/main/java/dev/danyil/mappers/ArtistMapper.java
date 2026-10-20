package dev.danyil.mappers;

import java.util.Comparator;
// import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.artists.Social;
import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialRequestDTO;
import dev.danyil.artists.dtos.SocialResponseDTO;
import dev.danyil.users.UserEntity;

@Component 
public class ArtistMapper {

    public ArtistEntity toEntity(ArtistCreateRequestDTO request, UserEntity owner) {
        ArtistEntity artist = new ArtistEntity();
        artist.setName(request.name().trim());
        artist.setAvatarUrl(blankToNull(request.avatarUrl()));
        artist.setBannerUrl(blankToNull(request.bannerUrl()));
        artist.setDescription(blankToNull(request.description()));
        artist.setGenre(blankToNull(request.genre()));
        artist.getSocials().addAll(toSocials(request.socials()));
        artist.setUser(owner);
        return artist;
    }

    // Applies only the fields present in the request; null fields are left unchanged.
    public void applyUpdate(ArtistUpdateRequestDTO request, ArtistEntity artist) {
        if (request.name() != null) {
            artist.setName(request.name().trim());
        }
        if (request.avatarUrl() != null) {
            artist.setAvatarUrl(blankToNull(request.avatarUrl()));
        }
        if (request.bannerUrl() != null) {
            artist.setBannerUrl(blankToNull(request.bannerUrl()));
        }
        if (request.description() != null) {
            artist.setDescription(blankToNull(request.description()));
        }
        if (request.genre() != null) {
            artist.setGenre(blankToNull(request.genre()));
        }
        if (request.socials() != null) {
            artist.getSocials().clear();
            artist.getSocials().addAll(toSocials(request.socials()));
        }
    }

    public ArtistResponseDTO toResponse(ArtistEntity artist) {
        List<SocialResponseDTO> socials = artist.getSocials().stream()
            .sorted(Comparator.comparing(Social::getPlatform))
            .map(social -> SocialResponseDTO.builder()
                .platform(social.getPlatform())
                .url(social.getUrl())
                .build())
            .toList();
        
        return ArtistResponseDTO.builder()
            .id(artist.getId())
            .name(artist.getName())
            .avatarUrl(artist.getAvatarUrl())
            .bannerUrl(artist.getBannerUrl())
            .description(artist.getDescription())
            .genre(artist.getGenre())
            .socials(socials)
            .releaseCount(artist.getReleaseCount())
        .build();
    }

    private Set<Social> toSocials(List<SocialRequestDTO> requests) {
        if (requests == null) {
            return Set.of();
        }
        return requests.stream()
            .map(request -> new Social(request.platform(), request.url().trim()))
            .collect(Collectors.toSet());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
