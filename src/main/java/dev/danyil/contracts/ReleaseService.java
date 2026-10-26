package dev.danyil.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseDatesRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleaseGenresRequestDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;

public interface ReleaseService extends GenericEditOfUserService<ReleaseUpdateRequestDTO, ReleaseResponseDTO>  {

    ReleaseResponseDTO create(ReleaseCreateRequestDTO dto, Long userId);
    
    Page<ReleaseResponseDTO> getAll(Long userId, ReleaseFilterDTO filter, Pageable pageable);

    ReleaseResponseDTO getById(Long userId, Long id);

    ReleaseResponseDTO updatePhase(Long userId, Long id, ReleasePhaseUpdateRequestDTO dto);

    ReleaseResponseDTO uploadArtwork(Long userId, Long id, MultipartFile file);

    ReleaseResponseDTO removeArtwork(Long userId, Long id);

    ReleaseResponseDTO updateGenres(Long userId, Long id, ReleaseGenresRequestDTO request);

    ReleaseResponseDTO updateDates(Long userId, Long id, ReleaseDatesRequestDTO request);

}
