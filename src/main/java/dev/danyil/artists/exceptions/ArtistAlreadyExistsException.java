package dev.danyil.artists.exceptions;

import lombok.Getter;

@Getter 
public class ArtistAlreadyExistsException extends RuntimeException {
    private final String name;

    public ArtistAlreadyExistsException(String name) {
        super("Artist with name '" + name + "' already exists");
        this.name = name;
    }
}
