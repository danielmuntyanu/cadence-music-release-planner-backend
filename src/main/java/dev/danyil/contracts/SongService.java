package dev.danyil.contracts;

import java.util.List;

import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;


public interface SongService {

    List<SongResponseDTO> getAll(Long userId, Long releaseId);

    SongResponseDTO getById(Long userId, Long releaseId, Long songId);

    SongResponseDTO create(Long userId, Long releaseId, SongRequestDTO request);

    SongResponseDTO replace(Long userId, Long releaseId, Long songId, SongRequestDTO request);

    SongResponseDTO updateStatus(Long userId, Long releaseId, Long songId, SongStatusUpdateRequestDTO request);

    void delete(Long userId, Long releaseId, Long songId);

    List<SongResponseDTO> reorder(Long userId, Long releaseId, SongOrderRequestDTO request);

}
