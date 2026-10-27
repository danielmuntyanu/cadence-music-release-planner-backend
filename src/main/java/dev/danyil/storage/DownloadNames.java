package dev.danyil.storage;

/** File names for downloads: what the user typed, made safe for every file system. */
public final class DownloadNames {

    private DownloadNames() {}

    /** "mix 3 / final?" + "wav" → "mix 3 _ final_.wav" */
    public static String of(String baseName, String extension) {
        String safe = baseName == null ? "" : baseName
            .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
            .strip();
        return (safe.isEmpty() ? "download" : safe) + "." + extension;
    }

    /** "users/1/song-files/9fc1….wav" → "wav" */
    public static String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        return dot > slash ? key.substring(dot + 1) : "bin";
    }
}