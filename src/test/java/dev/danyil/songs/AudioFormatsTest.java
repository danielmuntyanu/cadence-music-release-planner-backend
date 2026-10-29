package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AudioFormatsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "mix3.wav,     wav",
        "MASTER.WAV,   wav",
        "take.Aiff,    aiff",
        "take.aif,     aif",
        "song.flac,    flac",
        "demo.mp3,     mp3",
        "voice.m4a,    m4a",
        "clip.aac,     aac",
        "loop.ogg,     ogg",
        "users/1/song-files/9fc1.wav, wav",
    })
    void extension_acceptsKnownAudioInAnyCase(String filename, String expected) {
        assertThat(AudioFormats.extension(filename), is(Optional.of(expected)));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = { "notes.txt", "cover.png", "no-extension", "trailing-dot.", "", "archive.wav.zip" })
    void extension_rejectsAnythingElse(String filename) {
        assertThat(AudioFormats.extension(filename), is(Optional.empty()));
    }

    @Test
    void contentType_isCanonical_whateverTheBrowserReported() {
        assertThat(AudioFormats.contentType("wav"), is("audio/wav"));
        assertThat(AudioFormats.contentType("aif"), is("audio/aiff"));
        assertThat(AudioFormats.contentType("m4a"), is("audio/mp4"));
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "I Belong To You mix3.wav | I Belong To You mix3",
        "master_v_5.final.wav     | master_v_5.final",
        "no-extension             | no-extension",
        ".hidden                  | .hidden",
    })
    void nameWithoutExtension_dropsOnlyTheLastPart(String filename, String expected) {
        assertThat(AudioFormats.nameWithoutExtension(filename.strip()), is(expected.strip()));
    }
}