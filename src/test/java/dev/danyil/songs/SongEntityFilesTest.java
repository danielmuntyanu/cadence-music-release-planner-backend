package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.danyil.songs.exceptions.SongFileNotFoundException;

/** The heart rules: at most one primary file, and only one of the song's own files. */
class SongEntityFilesTest {

    private SongEntity song;

    @BeforeEach
    void setUp() {
        song = SongEntity.builder().title("Night Drive").build();
    }

    private static SongFileEntity file(long id, String name) {
        SongFileEntity file = SongFileEntity.builder()
            .storageKey("users/1/song-files/" + id + ".wav")
            .displayName(name)
            .originalFilename(name + ".wav")
            .contentType("audio/wav")
            .sizeBytes(1)
            .build();
        file.setId(id);
        return file;
    }

    @Test
    void addFile_linksBackToTheSong() {
        SongFileEntity demo = song.addFile(file(1, "demo"));

        assertThat(demo.getSong(), is(sameInstance(song)));
    }

    @Test
    void addFile_keepsTheNewestFirst() {
        song.addFile(file(1, "demo"));
        song.addFile(file(2, "mix3"));

        assertThat(song.getFiles().stream().map(SongFileEntity::getDisplayName).toList(), contains("mix3", "demo"));
    }

    @Test
    void firstFile_becomesPrimary_laterFilesDoNot() {
        SongFileEntity demo = song.addFile(file(1, "demo"));
        song.addFile(file(2, "mix3"));

        assertThat(song.getPrimaryFile(), is(sameInstance(demo)));
    }

    @Test
    void setPrimaryFile_movesTheHeart() {
        song.addFile(file(1, "demo"));
        SongFileEntity master = song.addFile(file(2, "master"));

        song.setPrimaryFile(master);

        assertThat(song.getPrimaryFile(), is(sameInstance(master)));
    }

    @Test
    void setPrimaryFile_null_clearsTheHeart() {
        song.addFile(file(1, "demo"));

        song.setPrimaryFile(null);

        assertThat(song.getPrimaryFile(), is(nullValue()));
    }

    @Test
    void setPrimaryFile_ofAnotherSong_isRejected() {
        SongFileEntity demo = song.addFile(file(1, "demo"));
        SongFileEntity foreign = SongEntity.builder().title("Other").build().addFile(file(9, "foreign"));

        assertThrows(SongFileNotFoundException.class, () -> song.setPrimaryFile(foreign));

        assertThat(song.getPrimaryFile(), is(sameInstance(demo)));
    }

    @Test
    void removingThePrimaryFile_leavesNoPrimary() {
        // A new heart is a deliberate choice, so it is not guessed
        SongFileEntity demo = song.addFile(file(1, "demo"));
        song.addFile(file(2, "mix3"));

        song.removeFile(demo);

        assertThat(song.getPrimaryFile(), is(nullValue()));
        assertThat(song.getFiles().stream().map(SongFileEntity::getDisplayName).toList(), contains("mix3"));
    }

    @Test
    void removingAnotherFile_keepsThePrimary() {
        SongFileEntity demo = song.addFile(file(1, "demo"));
        SongFileEntity mix = song.addFile(file(2, "mix3"));

        song.removeFile(mix);

        assertThat(song.getPrimaryFile(), is(sameInstance(demo)));
    }

    @Test
    void removingAFileOfAnotherSong_changesNothing() {
        SongFileEntity demo = song.addFile(file(1, "demo"));
        SongFileEntity foreign = SongEntity.builder().title("Other").build().addFile(file(9, "foreign"));

        song.removeFile(foreign);

        assertThat(song.getFiles(), contains(demo));
        assertThat(song.getPrimaryFile(), is(sameInstance(demo)));
    }
}