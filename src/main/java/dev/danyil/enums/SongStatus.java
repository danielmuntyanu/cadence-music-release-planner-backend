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
    DONE;

    /** How far along the workflow this stage is: WRITING = 0.0, DONE = 1.0. */
    public double progress() {
        return ordinal() / (double) (values().length - 1);
    }
}