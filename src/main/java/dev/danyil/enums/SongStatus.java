package dev.danyil.enums;

/**
 * Production stage of a song, in workflow order.
 * The order matters: release progress counts how far each song has come.
 */
public enum SongStatus {
    WRITING,
    ARRANGEMENT,
    RECORDING,
    MIXING_MASTERING,
    DONE
}