package dev.danyil.contracts;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;

public interface ArtistService
        extends GenericGetOfUserService<ArtistResponseDTO>,
        GenericEditOfUserService<ArtistUpdateRequestDTO, ArtistResponseDTO> {

    ArtistResponseDTO create(ArtistCreateRequestDTO dto, Long userId);

}
