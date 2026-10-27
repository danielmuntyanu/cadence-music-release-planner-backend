package dev.danyil.releases.exceptions;

/** The release has no stored cover to download. Maps to 404. */
public class ArtworkNotFoundException extends RuntimeException {

    public ArtworkNotFoundException(Long releaseId) {
        super("Release " + releaseId + " has no uploaded cover");
    }
}