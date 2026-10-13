package dev.danyil.artists.exceptions;

import dev.danyil.enums.SocialPlatform;
import lombok.Getter;

@Getter 
public class DuplicateSocialPlatformException extends RuntimeException {
    private final SocialPlatform platform;

    public DuplicateSocialPlatformException(SocialPlatform platform) {
        super("Social with platform '" + platform.name() + "' already exists at this artist profile");
        this.platform = platform;
    }
}
