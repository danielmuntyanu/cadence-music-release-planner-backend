package dev.danyil.media;

/**
 * Storage key prefixes. Everything a user owns lives under users/{id}/,
 * which keeps per-user cleanup and quotas simple later on.
 */
public final class MediaKeys {

    private MediaKeys() {}

    public static String userAvatar(long userId) {
        return "users/%d/avatar".formatted(userId);
    }

    public static String artistAvatar(long userId, long artistId) {
        return "users/%d/artists/%d/avatar".formatted(userId, artistId);
    }

    public static String artistBanner(long userId, long artistId) {
        return "users/%d/artists/%d/banner".formatted(userId, artistId);
    }

    public static String releaseArtwork(long userId, long releaseId) {
        return "users/%d/releases/%d/artwork".formatted(userId, releaseId);
    }
}