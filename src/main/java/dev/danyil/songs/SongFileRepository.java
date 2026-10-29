package dev.danyil.songs;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SongFileRepository extends JpaRepository<SongFileEntity, Long> {

    boolean existsByStorageKey(String storageKey);
}