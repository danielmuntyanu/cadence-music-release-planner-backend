package dev.danyil.mappers;

import java.util.Locale;

import org.springframework.stereotype.Component;

import dev.danyil.songs.SongEntity;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;

@Component
public class SongMapper {

    public SongResponseDTO toResponse(SongEntity song) {
        return SongResponseDTO.builder()
            .id(song.getId())
            .trackNumber(song.getPosition() + 1)
            .title(song.getTitle())
            .durationSeconds(song.getDurationSeconds())
            .isrc(song.getIsrc())
            .status(song.getStatus())
            .lyrics(song.getLyrics())
            .explicit(song.isExplicit())
            .primaryFileId(song.getPrimaryFile() != null ? song.getPrimaryFile().getId() : null)
            .fileCount(song.getFiles().size())
            .createdAt(song.getCreatedAt())
            .build();
    }

    /** New song without release and position: ReleaseEntity.addSong sets both. */
    public SongEntity toEntity(SongRequestDTO dto) {
        return SongEntity.builder()
            .title(dto.title().trim())
            .durationSeconds(dto.durationSeconds())
            .isrc(normalizeIsrc(dto.isrc()))
            .status(dto.status())
            .lyrics(blankToNull(dto.lyrics()))
            .explicit(Boolean.TRUE.equals(dto.explicit()))
            .build();
    }

    /** PUT: every field is replaced; null and blank clear the optional ones. */
    public void replace(SongEntity song, SongRequestDTO dto) {
        song.setTitle(dto.title().trim());
        song.setDurationSeconds(dto.durationSeconds());
        song.setIsrc(normalizeIsrc(dto.isrc()));
        song.setStatus(dto.status());
        song.setLyrics(blankToNull(dto.lyrics()));
        song.setExplicit(Boolean.TRUE.equals(dto.explicit()));
    }

    /** "us-abc-26-00001" → "USABC2600001"; null for blank. */
    static String normalizeIsrc(String isrc) {
        String value = blankToNull(isrc);
        return value == null ? null : value.replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}