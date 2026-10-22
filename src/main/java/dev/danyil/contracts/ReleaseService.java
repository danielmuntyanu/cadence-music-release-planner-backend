package dev.danyil.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;

public interface ReleaseService extends 
        GenericGetOfUserService<ReleaseResponseDTO>, 
        GenericEditOfUserService<ReleaseUpdateRequestDTO, ReleaseResponseDTO>  {

    ReleaseResponseDTO create(ReleaseCreateRequestDTO dto, Long userId);
    
    ReleaseResponseDTO updatePhase(ReleasePhaseUpdateRequestDTO dto, Long userId, Long id);

    Page<ReleaseResponseDTO> getAll(Long userId, ReleaseFilterDTO filter, Pageable pageable);
}
