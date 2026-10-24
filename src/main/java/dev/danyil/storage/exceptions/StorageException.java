package dev.danyil.storage.exceptions;

/** The storage backend failed or is unreachable; not caused by the client's request. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}