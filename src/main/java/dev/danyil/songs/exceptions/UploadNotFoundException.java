package dev.danyil.songs.exceptions;

/** A confirmed upload is not in storage, not complete, already used, or not this user's. Maps to 400. */
public class UploadNotFoundException extends RuntimeException {

    public UploadNotFoundException() {
        super("The upload was not found. Upload the file again.");
    }
}