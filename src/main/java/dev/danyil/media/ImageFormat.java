package dev.danyil.media;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Image formats accepted as uploads, recognised by content rather than by file name. */
public enum ImageFormat {

    JPEG("image/jpeg", ".jpg", Set.of("jpeg", "jpg")),
    PNG("image/png", ".png", Set.of("png")),
    WEBP("image/webp", ".webp", Set.of("webp"));

    private final String contentType;
    private final String extension;
    private final Set<String> readerNames;

    ImageFormat(String contentType, String extension, Set<String> readerNames) {
        this.contentType = contentType;
        this.extension = extension;
        this.readerNames = readerNames;
    }

    public String contentType() {
        return contentType;
    }

    /** With the leading dot, like ProcessedImage.extension(). */
    public String extension() {
        return extension;
    }

    /** Maps an ImageIO reader format name ("JPEG", "png", "webp") to an accepted format. */
    static Optional<ImageFormat> fromReaderName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(format -> format.readerNames.contains(normalized)).findFirst();
    }
}