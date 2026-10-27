package dev.danyil.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DownloadNamesTest {

    @Test
    void of_keepsAnOrdinaryNameAndAddsTheExtension() {
        assertThat(DownloadNames.of("I Belong To You mix3", "wav"), is("I Belong To You mix3.wav"));
    }

    @Test
    void of_keepsAccentsAndCyrillic() {
        // Only characters file systems reject are replaced, not everything outside ASCII
        assertThat(DownloadNames.of("Lía Montes — Ніч", "png"), is("Lía Montes — Ніч.png"));
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "mix 3 / final?    | mix 3 _ final_.wav",
        "a\\b:c*d          | a_b_c_d.wav",
        "\"quoted\" <tag>  | _quoted_ _tag_.wav",
    })
    void of_replacesCharactersFileSystemsReject(String name, String expected) {
        assertThat(DownloadNames.of(name.strip(), "wav"), is(expected.strip()));
    }

    @Test
    void of_replacesAPipe() {
        // Not in the table above: "|" is that table's own column separator
        assertThat(DownloadNames.of("pipe|name", "wav"), is("pipe_name.wav"));
    }

    @Test
    void of_trimsSurroundingSpaces() {
        assertThat(DownloadNames.of("  mix  ", "wav"), is("mix.wav"));
    }

    @Test
    void of_blankOrNullName_fallsBackToDownload() {
        assertThat(DownloadNames.of("   ", "wav"), is("download.wav"));
        assertThat(DownloadNames.of(null, "wav"), is("download.wav"));
    }

    @Test
    void extensionOf_readsTheLastPartOfTheFileName() {
        assertThat(DownloadNames.extensionOf("users/1/song-files/9fc1b67e.wav"), is("wav"));
    }

    @Test
    void extensionOf_ignoresDotsInFolderNames() {
        assertThat(DownloadNames.extensionOf("users/v1.5/file"), is("bin"));
    }

    @Test
    void extensionOf_withoutExtension_isBin() {
        assertThat(DownloadNames.extensionOf("users/1/file"), is("bin"));
    }
}