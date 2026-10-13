package dev.danyil.artists.exceptions;

import lombok.Getter;

@Getter 
public class ArtistNotFoundException extends RuntimeException {
    private final Long id;

    public ArtistNotFoundException(Long id) {
        super("Artist with id '" + id + "' not found");
        this.id = id;
    }
}
