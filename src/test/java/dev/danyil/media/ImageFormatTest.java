package dev.danyil.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageFormatTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({ "JPEG, JPEG", "jpeg, JPEG", "jpg, JPEG", "png, PNG", "PNG, PNG", "webp, WEBP" })
    void fromReaderName_ignoresCase(String readerName, ImageFormat expected) {
        assertThat(ImageFormat.fromReaderName(readerName), is(Optional.of(expected)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "gif", "bmp", "tiff" })
    void fromReaderName_rejectsOtherFormats(String readerName) {
        assertThat(ImageFormat.fromReaderName(readerName), is(Optional.empty()));
    }

    @Test
    void extensionHasTheLeadingDot_likeProcessedImages() {
        assertThat(ImageFormat.PNG.extension(), is(".png"));
        assertThat(ImageFormat.JPEG.contentType(), is("image/jpeg"));
    }
}