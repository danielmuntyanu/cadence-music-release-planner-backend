package dev.danyil.releases.exceptions;

import lombok.Getter;

@Getter 
public class ReleaseNotFoundException extends RuntimeException {
    private final Long id;

    public ReleaseNotFoundException(Long id) {
        super("Release with id '" + id + "' not found");
        this.id = id;
    }

}
