package dev.danyil.songs;

import org.springframework.stereotype.Component;

import dev.danyil.releases.ReleaseFinder;
import dev.danyil.songs.exceptions.SongNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * A song counts as found only inside the user's release from the URL:
 * /releases/1/songs/99 must not reach a song of release 2, even one the user owns.
 */
@Component
@RequiredArgsConstructor
public class SongFinder {

    private final ReleaseFinder releaseFinder;
    private final SongRepository songRepository;

    public SongEntity findOwned(Long userId, Long releaseId, Long songId) {
        releaseFinder.findOwned(userId, releaseId);
        return songRepository.findByIdAndReleaseId(songId, releaseId)
            .orElseThrow(() -> new SongNotFoundException(songId));
    }
}