package dev.danyil.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;

public interface ReleaseService extends GenericEditOfUserService<ReleaseUpdateRequestDTO, ReleaseResponseDTO>  {

    ReleaseResponseDTO create(ReleaseCreateRequestDTO dto, Long userId);
    
    ReleaseResponseDTO updatePhase(Long userId, Long id, ReleasePhaseUpdateRequestDTO dto);

    Page<ReleaseResponseDTO> getAll(Long userId, ReleaseFilterDTO filter, Pageable pageable);

    ReleaseResponseDTO getById(Long userId, Long id);
}
