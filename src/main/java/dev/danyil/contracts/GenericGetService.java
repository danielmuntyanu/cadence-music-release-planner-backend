package dev.danyil.contracts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GenericGetService<T> {

    Page<T> getAll(Pageable pageable);
    T getById(Long id);

}
