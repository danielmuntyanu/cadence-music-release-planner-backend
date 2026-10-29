package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.danyil.enums.Genre;
import dev.danyil.enums.SongStatus;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.songs.exceptions.InvalidSongOrderException;

/** Tracklist rules owned by ReleaseEntity: gapless positions and safe reordering. */
class SongEntityTest {

    private ReleaseEntity release;

    @BeforeEach
    void setUp() {
        release = ReleaseEntity.builder().name("Night Drive").primaryGenre(Genre.POP).build();
    }

    private SongEntity song(long id, String title) {
        SongEntity song = SongEntity.builder().title(title).build();
        song.setId(id);
        return release.addSong(song);
    }

    private List<String> titles() {
        return release.getSongs().stream().map(SongEntity::getTitle).toList();
    }

    private List<Integer> positions() {
        return release.getSongs().stream().map(SongEntity::getPosition).toList();
    }

    @Test
    void builder_withoutStatus_startsInWriting() {
        assertThat(SongEntity.builder().title("Intro").build().getStatus(), is(SongStatus.WRITING));
    }

    @Test
    void addSong_appendsAtTheEnd_andLinksBackToTheRelease() {
        SongEntity intro = song(1, "Intro");
        song(2, "Night Drive");

        assertThat(titles(), contains("Intro", "Night Drive"));
        assertThat(positions(), contains(0, 1));
        assertThat(intro.getRelease(), is(sameInstance(release)));
    }

    @Test
    void removeSong_closesTheGap() {
        song(1, "Intro");
        SongEntity middle = song(2, "Night Drive");
        song(3, "Outro");

        release.removeSong(middle);

        assertThat(titles(), contains("Intro", "Outro"));
        assertThat(positions(), contains(0, 1));
    }

    @Test
    void removeSong_ofAnotherRelease_changesNothing() {
        song(1, "Intro");
        SongEntity stranger = SongEntity.builder().title("Elsewhere").build();

        release.removeSong(stranger);

        assertThat(titles(), contains("Intro"));
    }

    @Test
    void reorderSongs_appliesTheNewOrder() {
        song(1, "Intro");
        song(2, "Night Drive");
        song(3, "Outro");

        release.reorderSongs(List.of(3L, 1L, 2L));

        assertThat(titles(), contains("Outro", "Intro", "Night Drive"));
        assertThat(positions(), contains(0, 1, 2));
    }

    @Test
    void reorderSongs_missingSong_isRejectedAndKeepsTheOrder() {
        song(1, "Intro");
        song(2, "Night Drive");

        assertThrows(InvalidSongOrderException.class, () -> release.reorderSongs(List.of(2L)));

        assertThat(titles(), contains("Intro", "Night Drive"));
    }

    @Test
    void reorderSongs_unknownSong_isRejected() {
        song(1, "Intro");
        song(2, "Night Drive");

        assertThrows(InvalidSongOrderException.class, () -> release.reorderSongs(List.of(1L, 99L)));
    }

    @Test
    void reorderSongs_duplicateSong_isRejected() {
        song(1, "Intro");
        song(2, "Night Drive");

        assertThrows(InvalidSongOrderException.class, () -> release.reorderSongs(List.of(1L, 1L)));
    }

    @Test
    void builder_withoutExplicit_isNotExplicit() {
        assertThat(SongEntity.builder().title("Intro").build().isExplicit(), is(false));
    }
}