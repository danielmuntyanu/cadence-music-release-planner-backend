package dev.danyil.songs;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.contracts.SongService;
import dev.danyil.mappers.SongMapper;
import dev.danyil.media.ImageUploadService;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.ReleaseFinder;
import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongServiceImpl implements SongService {

    private final ReleaseFinder releaseFinder;
    private final SongFinder songFinder;
    private final SongRepository songRepository;
    private final SongMapper songMapper;
    // Deletes a key only after the transaction commits; works for any media, not only images
    private final ImageUploadService mediaCleanup;

    @Override
    public List<SongResponseDTO> getAll(Long userId, Long releaseId) {
        // Ordered by position through @OrderBy on ReleaseEntity.songs
        return releaseFinder.findOwned(userId, releaseId).getSongs().stream()
            .map(songMapper::toResponse)
            .toList();
    }

    @Override
    public SongResponseDTO getById(Long userId, Long releaseId, Long songId) {
        return songMapper.toResponse(songFinder.findOwned(userId, releaseId, songId));
    }

    @Override
    @Transactional
    public SongResponseDTO create(Long userId, Long releaseId, SongRequestDTO request) {
        ReleaseEntity release = releaseFinder.findOwned(userId, releaseId);

        SongEntity song = release.addSong(songMapper.toEntity(request));
        // Saved explicitly so the response already carries the generated id
        return songMapper.toResponse(songRepository.save(song));
    }

    @Override
    @Transactional
    public SongResponseDTO replace(Long userId, Long releaseId, Long songId, SongRequestDTO request) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);
        songMapper.replace(song, request);
        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public SongResponseDTO updateStatus(Long userId, Long releaseId, Long songId, SongStatusUpdateRequestDTO request) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);
        song.setStatus(request.status());
        return songMapper.toResponse(song);
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

    @Override
    @Transactional
    public void delete(Long userId, Long releaseId, Long songId) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);

        // Rows go by cascade; the audio in storage has to be removed explicitly, after commit
        song.getFiles().forEach(file -> mediaCleanup.deleteAfterCommit(file.getStorageKey()));
        // orphanRemoval deletes the row; removeSong closes the gap in track numbers
        song.getRelease().removeSong(song);
    }
}