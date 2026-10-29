package dev.danyil.artists.exceptions;

public class ArtistOwnershipException extends RuntimeException {
    public ArtistOwnershipException() {
        super("Artist ownership error. Permission denied");
    }
}
