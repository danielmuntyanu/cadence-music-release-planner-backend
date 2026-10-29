package dev.danyil.enums;

import dev.danyil.media.ImageKind;
import dev.danyil.media.MediaKeys;

/** The two image slots of an artist; lets one upload flow serve both endpoints. */
public enum ArtistImage {

    AVATAR(ImageKind.AVATAR),
    BANNER(ImageKind.BANNER);

    private final ImageKind kind;

    ArtistImage(ImageKind kind) {
        this.kind = kind;
    }

    public ImageKind kind() {
        return kind;
    }

    public String keyPrefix(long userId, long artistId) {
        return this == AVATAR
            ? MediaKeys.artistAvatar(userId, artistId)
            : MediaKeys.artistBanner(userId, artistId);
    }
}