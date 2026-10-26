package dev.danyil.mappers;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.danyil.media.MediaUrlResolver;
import dev.danyil.release_artists.ReleaseArtistEntity;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.dtos.ReleaseArtistResponseDTO;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor 
public class ReleaseMapper {

    private final MediaUrlResolver media;

    public ReleaseResponseDTO toResponse(ReleaseEntity entity) {
        return ReleaseResponseDTO.builder()
            .id(entity.getId())
            .name(entity.getName())
            .type(entity.getType())
            .phase(entity.getPhase())
            .artworkUrl(media.resolve(entity.getArtworkKey(), entity.getArtworkUrl()))            .notes(entity.getNotes())
            .primaryGenre(entity.getPrimaryGenre())
            .secondaryGenre(entity.getSecondaryGenre())
            .releaseDate(entity.getReleaseDate())
            .originalReleaseDate(entity.getOriginalReleaseDate())
            .artists(toArtistResponses(entity.getArtists()))
            .createdAt(entity.getCreatedAt())
            .build();
    }

    // New entity without user and artists: the service sets both.
    public ReleaseEntity toEntity(ReleaseCreateRequestDTO dto) {
        return ReleaseEntity.builder()
            .name(dto.name().trim())
            .type(dto.type())
            .phase(dto.phase())
            .notes(blankToNull(dto.notes()))
            .primaryGenre(dto.primaryGenre())
            .secondaryGenre(dto.secondaryGenre())
            .releaseDate(dto.releaseDate())
            .originalReleaseDate(dto.originalReleaseDate())
            .build();
    }

    // Applies non-null fields; blank notes clear the field. Artists are handled by the service.
    public void applyUpdate(ReleaseEntity entity, ReleaseUpdateRequestDTO dto) {
        if (dto.name() != null) entity.setName(dto.name().trim());
        if (dto.type() != null) entity.setType(dto.type());
        if (dto.notes() != null) entity.setNotes(blankToNull(dto.notes()));
    }

    private List<ReleaseArtistResponseDTO> toArtistResponses(List<ReleaseArtistEntity> links) {
        return links.stream()
            .map(ReleaseArtistEntity::getArtist)
            .map(artist -> ReleaseArtistResponseDTO.builder()
                .id(artist.getId())
                .name(artist.getName())
                .avatarUrl(media.resolve(artist.getAvatarKey(), artist.getAvatarUrl()))
                .build())
            .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}