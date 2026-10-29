package dev.danyil.songs.exceptions;

/** Maps to 404. */
public class SongFileNotFoundException extends RuntimeException {

    public SongFileNotFoundException(Long id) {
        super("Song file " + id + " not found");
    }
}