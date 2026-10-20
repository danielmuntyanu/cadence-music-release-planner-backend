package dev.danyil.releases.exceptions;

import lombok.Getter;

@Getter 
public class ReleaseAlreadyExistsException extends RuntimeException {
    private final String name;

    public ReleaseAlreadyExistsException(String name) {
        super("Release with name '" + name + "' already exists");
        this.name = name;
    }

}
