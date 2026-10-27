package dev.danyil.media;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

/**
 * Turns an uploaded file into a safe, normalized JPEG:
 * checks the real format and dimensions, applies EXIF rotation, resizes or crops,
 * flattens transparency and re-encodes without metadata (no GPS or camera data).
 *
 * The same checks are available on their own through inspect(), for originals
 * that must be stored byte for byte.
 */
@Component
public class ImageProcessor {

    // Guards against "decompression bombs": tiny files that decode to huge bitmaps
    private static final int MAX_SOURCE_SIDE = 8000;
    private static final float JPEG_QUALITY = 0.85f;

    /** What the header says about an upload: its real format and its size. */
    private record SourceInfo(ImageFormat format, Dimension size) {}

    public ProcessedImage process(byte[] source, ImageKind kind) {
        Dimension size = readSource(source).size();
        BufferedImage resized = resize(source, size, kind);
        byte[] jpeg = encodeJpeg(flatten(resized));

        return new ProcessedImage(jpeg, "image/jpeg", ".jpg");
    }

    /**
     * Checks an upload without changing it: a real JPEG, PNG or WebP by content,
     * within the size limit. Returns its format, for storing the original as is.
     */
    public ImageFormat inspect(byte[] source) {
        return readSource(source).format();
    }

    /** Reads only the header: format and dimensions, without decoding the pixels. */
    private SourceInfo readSource(byte[] source) {
        if (source == null || source.length == 0) throw new InvalidImageException("The file is empty");

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidImageException("The file is not an image");

            ImageReader reader = readers.next();
            try {
                ImageFormat format = ImageFormat.fromReaderName(reader.getFormatName())
                    .orElseThrow(() -> new InvalidImageException("Only JPEG, PNG and WebP images are supported"));

                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
                    throw new InvalidImageException("The image is too large (max %d px per side)".formatted(MAX_SOURCE_SIDE));
                }
                return new SourceInfo(format, new Dimension(width, height));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("The image could not be read");
        }
    }

    /** Never upscales: a small image keeps its size instead of turning blurry. */
    private BufferedImage resize(byte[] source, Dimension size, ImageKind kind) {
        try {
            // Reading from the stream (not a BufferedImage) lets Thumbnailator apply EXIF orientation
            var builder = Thumbnails.of(new ByteArrayInputStream(source));
            if (kind.square()) {
                int side = Math.min(kind.maxWidth(), Math.min(size.width, size.height));
                builder.size(side, side).crop(Positions.CENTER);
            } else if (size.width <= kind.maxWidth() && size.height <= kind.maxHeight()) {
                builder.scale(1.0);
            } else {
                builder.size(kind.maxWidth(), kind.maxHeight());
            }
            return builder.asBufferedImage();
        } catch (IOException e) {
            throw new InvalidImageException("The image could not be processed");
        }
    }

    /** JPEG has no transparency: transparent areas become white instead of black. */
    private static BufferedImage flatten(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    /** Writes pixels only: no metadata is carried over from the original file. */
    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}