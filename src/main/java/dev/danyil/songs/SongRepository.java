package dev.danyil.songs;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<SongEntity, Long> {

    /** A song only counts as found inside the release from the URL (/releases/{id}/songs/{songId}). */
    Optional<SongEntity> findByIdAndReleaseId(Long id, Long releaseId);

}
