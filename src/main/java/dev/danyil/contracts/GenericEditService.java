package dev.danyil.contracts;

public interface GenericEditService<T, S> {
    S update(Long id, T dto);
    void delete(Long id);
}
