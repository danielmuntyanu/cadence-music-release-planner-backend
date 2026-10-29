package dev.danyil.contracts;

import org.springframework.web.multipart.MultipartFile;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.enums.ArtistImage;

public interface ArtistService
        extends GenericGetOfUserService<ArtistResponseDTO>,
        GenericEditOfUserService<ArtistUpdateRequestDTO, ArtistResponseDTO> {

    ArtistResponseDTO create(ArtistCreateRequestDTO dto, Long userId);

    ArtistResponseDTO uploadImage(Long userId, Long id, ArtistImage image, MultipartFile file);

    ArtistResponseDTO removeImage(Long userId, Long id, ArtistImage image);

}
