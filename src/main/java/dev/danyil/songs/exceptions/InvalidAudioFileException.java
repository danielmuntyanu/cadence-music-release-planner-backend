package dev.danyil.songs.exceptions;

/** The file is not audio we accept, or is empty or too large. Maps to 400. */
public class InvalidAudioFileException extends RuntimeException {

    public InvalidAudioFileException(String message) {
        super(message);
    }
}