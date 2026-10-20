package dev.danyil.releases.exceptions;

public class ReleaseOwnershipException extends RuntimeException {
    public ReleaseOwnershipException() {
        super("Release ownership error. Permission denied");
    }
}
