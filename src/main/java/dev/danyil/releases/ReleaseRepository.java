package dev.danyil.releases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReleaseRepository extends JpaRepository<ReleaseEntity, Long>, JpaSpecificationExecutor<ReleaseEntity> {

    Page<ReleaseEntity> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

}
