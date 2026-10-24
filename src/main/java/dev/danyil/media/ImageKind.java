package dev.danyil.media;

/** Target size per image role. Square kinds are center-cropped; others keep their aspect ratio. */
public enum ImageKind {

    AVATAR(512, 512, true),
    ARTWORK(1400, 1400, true),
    BANNER(1600, 1600, false);

    private final int maxWidth;
    private final int maxHeight;
    private final boolean square;

    ImageKind(int maxWidth, int maxHeight, boolean square) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.square = square;
    }

    public int maxWidth() { return maxWidth; }
    public int maxHeight() { return maxHeight; }
    public boolean square() { return square; }
}