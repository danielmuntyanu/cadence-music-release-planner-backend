package dev.danyil.songs.exceptions;

/** A reorder request does not list exactly the songs of the release. Maps to 400. */
public class InvalidSongOrderException extends RuntimeException {

    public InvalidSongOrderException() {
        super("The new order must list every song of the release exactly once");
    }
}