package dev.danyil.media;

/** The uploaded file is not an acceptable image. The message is safe to show to the user. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}