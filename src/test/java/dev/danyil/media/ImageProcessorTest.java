package dev.danyil.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** Pure unit tests: images are generated in memory, no files or storage involved. */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static byte[] image(String format, int width, int height, boolean transparent) throws IOException {
        int type = transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        if (!transparent) {
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static BufferedImage decode(ProcessedImage processed) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(processed.content()));
    }

    @Test
    void avatar_largeImage_isCenterCroppedToSquare() throws IOException {
        ProcessedImage result = processor.process(image("png", 1200, 800, false), ImageKind.AVATAR);

        BufferedImage output = decode(result);
        assertThat(output.getWidth(), is(512));
        assertThat(output.getHeight(), is(512));
    }

    @Test
    void avatar_smallImage_isCroppedButNotUpscaled() throws IOException {
        BufferedImage output = decode(processor.process(image("png", 300, 200, false), ImageKind.AVATAR));

        assertThat(output.getWidth(), is(200));
        assertThat(output.getHeight(), is(200));
    }

    @Test
    void banner_keepsAspectRatioWithinLimits() throws IOException {
        BufferedImage output = decode(processor.process(image("png", 3200, 1000, false), ImageKind.BANNER));

        assertThat(output.getWidth(), is(1600));
        assertThat(output.getHeight(), is(500));
    }

    @Test
    void banner_smallImage_keepsItsSize() throws IOException {
        BufferedImage output = decode(processor.process(image("png", 800, 300, false), ImageKind.BANNER));

        assertThat(output.getWidth(), is(800));
        assertThat(output.getHeight(), is(300));
    }

    @Test
    void output_isAlwaysJpeg() throws IOException {
        ProcessedImage result = processor.process(image("png", 100, 100, false), ImageKind.ARTWORK);

        assertThat(result.contentType(), is("image/jpeg"));
        assertThat(result.extension(), is(".jpg"));
        // JPEG files start with the SOI marker FF D8
        assertThat(result.content()[0], is((byte) 0xFF));
        assertThat(result.content()[1], is((byte) 0xD8));
    }

    @Test
    void transparentAreas_becomeWhite() throws IOException {
        BufferedImage output = decode(processor.process(image("png", 100, 100, true), ImageKind.ARTWORK));

        Color corner = new Color(output.getRGB(0, 0));
        // JPEG is lossy: allow a small deviation from pure white
        assertThat(corner.getRed() > 245 && corner.getGreen() > 245 && corner.getBlue() > 245, is(true));
    }

    @Test
    void emptyFile_isRejected() {
        assertThrows(InvalidImageException.class, () -> processor.process(new byte[0], ImageKind.AVATAR));
    }

    @Test
    void nonImageContent_isRejected_whateverItClaimsToBe() {
        byte[] text = "definitely not a picture".getBytes();

        assertThrows(InvalidImageException.class, () -> processor.process(text, ImageKind.AVATAR));
    }

    @Test
    void unsupportedFormat_isRejected() throws IOException {
        assertThrows(InvalidImageException.class,
            () -> processor.process(image("gif", 50, 50, false), ImageKind.AVATAR));
    }

    @Test
    void hugeDimensions_areRejectedBeforeDecoding() throws IOException {
        // 9000 x 10 is small in memory but exceeds the per-side limit
        assertThrows(InvalidImageException.class,
            () -> processor.process(image("png", 9000, 10, false), ImageKind.BANNER));
    }

    // ---------- inspect: checks without changing ----------

    private static byte[] encode(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void inspect_recognisesTheFormatByContent() throws Exception {
        assertThat(processor.inspect(encode("png", 10, 10)), is(ImageFormat.PNG));
        assertThat(processor.inspect(encode("jpg", 10, 10)), is(ImageFormat.JPEG));
    }

    @Test
    void inspect_rejectsWhatIsNotAnImage() {
        byte[] html = "<html>not a cover</html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidImageException.class, () -> processor.inspect(html));
    }

    @Test
    void inspect_rejectsAnUnsupportedFormat() throws Exception {
        assertThrows(InvalidImageException.class, () -> processor.inspect(encode("gif", 10, 10)));
    }

    @Test
    void inspect_rejectsImagesOverTheSideLimit() throws Exception {
        // 8001 × 1 is a tiny bitmap, so the test is fast; only the width is over the limit
        assertThrows(InvalidImageException.class, () -> processor.inspect(encode("png", 8001, 1)));
    }

    @Test
    void inspect_rejectsAnEmptyFile() {
        assertThrows(InvalidImageException.class, () -> processor.inspect(new byte[0]));
    }

    @Test
    void inspect_andProcess_applyTheSameChecks() throws Exception {
        byte[] tooWide = encode("png", 8001, 1);

        assertThrows(InvalidImageException.class, () -> processor.inspect(tooWide));
        assertThrows(InvalidImageException.class, () -> processor.process(tooWide, ImageKind.ARTWORK));
    }
}