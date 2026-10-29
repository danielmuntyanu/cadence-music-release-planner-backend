package dev.danyil.songs;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Audio types accepted for songs, keyed by file extension. Browsers report audio MIME types
 * inconsistently (audio/wav, audio/x-wav, audio/wave, or nothing in Safari), so the extension
 * decides and the type that is signed and stored is the canonical one below.
 */
final class AudioFormats {

    private static final Map<String, String> BY_EXTENSION = Map.of(
        "wav", "audio/wav",
        "aif", "audio/aiff",
        "aiff", "audio/aiff",
        "flac", "audio/flac",
        "mp3", "audio/mpeg",
        "m4a", "audio/mp4",
        "aac", "audio/aac",
        "ogg", "audio/ogg");

    private AudioFormats() {}

    /** The accepted extension of a file name or storage key, lowercase. */
    static Optional<String> extension(String filename) {
        if (filename == null) return Optional.empty();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return Optional.empty();
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.containsKey(extension) ? Optional.of(extension) : Optional.empty();
    }

    static String contentType(String extension) {
        return BY_EXTENSION.get(extension);
    }

    /** "I Belong To You mix3.wav" → "I Belong To You mix3" */
    static String nameWithoutExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename).trim();
    }
}