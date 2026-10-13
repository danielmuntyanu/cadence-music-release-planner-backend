package dev.danyil.artists;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<ArtistEntity, Long> {
    List<ArtistEntity> findAllByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
    
    Page<ArtistEntity> findAll(Pageable pageable);
}
