package dev.danyil.mappers;

import org.springframework.stereotype.Component;

import dev.danyil.contracts.StorageService;
import dev.danyil.songs.SongFileEntity;
import dev.danyil.songs.dtos.SongFileResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SongFileMapper {

    private final StorageService storage;

    public SongFileResponseDTO toResponse(SongFileEntity file) {
        return SongFileResponseDTO.builder()
            .id(file.getId())
            .displayName(file.getDisplayName())
            .originalFilename(file.getOriginalFilename())
            .contentType(file.getContentType())
            .sizeBytes(file.getSizeBytes())
            .uploadedAt(file.getUploadedAt())
            .primary(file == file.getSong().getPrimaryFile())
            .url(storage.signedReadUrl(file.getStorageKey()))
            .build();
    }
}