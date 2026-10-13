package dev.danyil.artists;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<ArtistEntity, Long> {
    Page<ArtistEntity> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
