package dev.danyil.artists;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<ArtistEntity, Long> {
    Page<ArtistEntity> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    List<ArtistEntity> findAllByIdInAndUserId(Collection<Long> ids, Long userId);
}
