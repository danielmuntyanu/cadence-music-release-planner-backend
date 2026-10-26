package dev.danyil.songs;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.contracts.SongService;
import dev.danyil.mappers.SongMapper;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.ReleaseFinder;
import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;
import dev.danyil.songs.exceptions.SongNotFoundException;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
@Transactional(readOnly = true)
public class SongServiceImpl implements SongService {

    private final ReleaseFinder releaseFinder;
    private final SongRepository songRepository;
    private final SongMapper songMapper;
    
    
    @Override
    @Transactional
    public SongResponseDTO create(Long userId, Long releaseId, SongRequestDTO request) {
        ReleaseEntity release = releaseFinder.findOwned(userId, releaseId);

        SongEntity song = release.addSong(songMapper.toEntity(request));
        // Saved explicitly so the response already carries the generated id
        return songMapper.toResponse(songRepository.save(song));
    }

    @Override
    public List<SongResponseDTO> getAll(Long userId, Long releaseId) {
        // Ordered by position through @OrderBy on ReleaseEntity.songs
        return releaseFinder.findOwned(userId, releaseId).getSongs().stream()
            .map(songMapper::toResponse)
            .toList();
    }

    @Override
    public SongResponseDTO getById(Long userId, Long releaseId, Long songId) {
        return songMapper.toResponse(findSong(userId, releaseId, songId));
    }

    @Override
    @Transactional
    public SongResponseDTO replace(Long userId, Long releaseId, Long songId, SongRequestDTO request) {
        SongEntity song = findSong(userId, releaseId, songId);
        songMapper.replace(song, request);
        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public SongResponseDTO updateStatus(Long userId, Long releaseId, Long songId, SongStatusUpdateRequestDTO request) {
        SongEntity song = findSong(userId, releaseId, songId);
        song.setStatus(request.status());
        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long releaseId, Long songId) {
        SongEntity song = findSong(userId, releaseId, songId);
        // orphanRemoval deletes the row; removeSong closes the gap in track numbers
        song.getRelease().removeSong(song);
    }

    @Override
    @Transactional
    public List<SongResponseDTO> reorder(Long userId, Long releaseId, SongOrderRequestDTO request) {
        ReleaseEntity release = releaseFinder.findOwned(userId, releaseId);

        // Throws InvalidSongOrderException unless the list names every song exactly once
        release.reorderSongs(request.songIds());

        return release.getSongs().stream()
            .map(songMapper::toResponse)
            .toList();
    }
    
    // HELPERS

    /**
     * Ownership is checked on the release first, then the song must belong to that release:
     * /releases/1/songs/99 must not reach a song of release 2, even one the user owns.
     */
    private SongEntity findSong(Long userId, Long releaseId, Long songId) {
        releaseFinder.findOwned(userId, releaseId);
        return songRepository.findByIdAndReleaseId(songId, releaseId)
            .orElseThrow(() -> new SongNotFoundException(songId));
    }

}
