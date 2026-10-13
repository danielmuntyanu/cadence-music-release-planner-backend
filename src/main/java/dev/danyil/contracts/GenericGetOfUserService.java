package dev.danyil.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GenericGetOfUserService<T> {
    Page<T> getAll(Long userId, Pageable pageable);
    T getById(Long userId, Long id);
}
