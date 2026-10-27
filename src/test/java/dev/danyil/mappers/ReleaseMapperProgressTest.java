package dev.danyil.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import dev.danyil.enums.Genre;
import dev.danyil.enums.SongStatus;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.songs.SongEntity;
import dev.danyil.users.UserEntity;

class ReleaseMapperProgressTest {

    private final ReleaseMapper mapper = new ReleaseMapper(key -> "signed:" + key);

    private static ReleaseEntity releaseWith(SongStatus... statuses) {
        ReleaseEntity release = ReleaseEntity.builder().name("Neon Siesta").primaryGenre(Genre.POP).build();
        release.setUser(new UserEntity());
        for (SongStatus status : statuses) {
            release.addSong(SongEntity.builder().title("Song").status(status).build());
        }
        return release;
    }

    @Test
    void statusProgress_runsFromZeroToOne() {
        assertThat(SongStatus.WRITING.progress(), is(0.0));
        assertThat(SongStatus.ARRANGEMENT.progress(), is(0.25));
        assertThat(SongStatus.MIXING_MASTERING.progress(), is(0.75));
        assertThat(SongStatus.DONE.progress(), is(1.0));
    }

    @Test
    void releaseWithoutSongs_hasNoProgress() {
        ReleaseResponseDTO response = mapper.toResponse(releaseWith());

        assertThat(response.songCount(), is(0));
        assertThat(response.doneSongCount(), is(0));
        assertThat(response.progressPercent(), is(0));
        assertThat(response.explicit(), is(false));
    }

    @Test
    void progress_isTheAverageOfTheSongs() {
        // 0 + 0.25 + 0.5 + 0.75 + 1 = 2.5, / 5 = 50 %
        ReleaseResponseDTO response = mapper.toResponse(releaseWith(SongStatus.values()));

        assertThat(response.songCount(), is(5));
        assertThat(response.doneSongCount(), is(1));
        assertThat(response.progressPercent(), is(50));
    }

    @Test
    void allDone_isOneHundredPercent() {
        ReleaseResponseDTO response = mapper.toResponse(releaseWith(SongStatus.DONE, SongStatus.DONE));

        assertThat(response.progressPercent(), is(100));
        assertThat(response.doneSongCount(), is(2));
    }

    @Test
    void oneExplicitSong_makesTheReleaseExplicit() {
        ReleaseEntity release = releaseWith(SongStatus.DONE);
        release.addSong(SongEntity.builder().title("Coral Static").explicit(true).build());

        assertThat(mapper.toResponse(release).explicit(), is(true));
    }

    @Test
    void progressPercent_roundsToWholeNumbers() {
        // (0 + 0.25 + 0.25) / 3 = 0.1666… → 17
        ReleaseEntity release = releaseWith(SongStatus.WRITING, SongStatus.ARRANGEMENT, SongStatus.ARRANGEMENT);

        assertThat(ReleaseMapper.progressPercent(release.getSongs()), is(17));
    }
}