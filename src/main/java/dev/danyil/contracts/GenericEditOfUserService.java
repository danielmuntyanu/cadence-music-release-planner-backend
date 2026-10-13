package dev.danyil.contracts;

public interface GenericEditOfUserService<T, S> {
    S update(Long userId, Long id, T dto);
    void delete(Long userId, Long id);
}
