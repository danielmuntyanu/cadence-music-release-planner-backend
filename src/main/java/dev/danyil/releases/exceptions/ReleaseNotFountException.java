package dev.danyil.releases.exceptions;

import lombok.Getter;

@Getter 
public class ReleaseNotFountException extends RuntimeException {
    private final Long id;

    public ReleaseNotFountException(Long id) {
        super("Release with id '" + id + "' not found");
        this.id = id;
    }

}
