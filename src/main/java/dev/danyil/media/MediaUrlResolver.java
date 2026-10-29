package dev.danyil.media;

/**
 * Turns a stored file into a URL the browser can load. Kept as a tiny interface so mappers
 * stay plain classes: tests pass a lambda instead of a storage backend.
 */
@FunctionalInterface
public interface MediaUrlResolver {

    /** Signed URL for a stored key; null for a null key. */
    String signedUrl(String key);

    /** An uploaded file wins over an external link; both may be absent. */
    default String resolve(String key, String externalUrl) {
        return key != null ? signedUrl(key) : externalUrl;
    }
}