package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.danyil.enums.Genre;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.ReleaseFinder;
import dev.danyil.releases.ReleaseRepository;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.songs.exceptions.SongNotFoundException;
import dev.danyil.users.UserEntity;

@ExtendWith(MockitoExtension.class)
class SongFinderTest {

    private static final long USER_ID = 1000L;
    private static final long OTHER_USER_ID = 2000L;
    private static final long RELEASE_ID = 5100L;
    private static final long SONG_ID = 7100L;

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private SongRepository songRepository;

    private SongFinder songFinder;
    private SongEntity song;

    @BeforeEach
    void setUp() {
        songFinder = new SongFinder(new ReleaseFinder(releaseRepository), songRepository);

        UserEntity owner = new UserEntity();
        owner.setId(USER_ID);
        ReleaseEntity release = ReleaseEntity.builder().name("Neon Siesta").primaryGenre(Genre.POP).build();
        release.setId(RELEASE_ID);
        release.setUser(owner);
        song = release.addSong(SongEntity.builder().title("Intro").build());

        when(releaseRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release));
    }

    @Test
    void findsTheSongInsideTheUsersRelease() {
        when(songRepository.findByIdAndReleaseId(SONG_ID, RELEASE_ID)).thenReturn(Optional.of(song));

        assertThat(songFinder.findOwned(USER_ID, RELEASE_ID, SONG_ID), sameInstance(song));
    }

    @Test
    void aSongOfAnotherRelease_isNotFound() {
        when(songRepository.findByIdAndReleaseId(SONG_ID, RELEASE_ID)).thenReturn(Optional.empty());

        assertThrows(SongNotFoundException.class, () -> songFinder.findOwned(USER_ID, RELEASE_ID, SONG_ID));
    }

    @Test
    void ownershipIsCheckedBeforeLookingForTheSong() {
        assertThrows(ReleaseOwnershipException.class, () -> songFinder.findOwned(OTHER_USER_ID, RELEASE_ID, SONG_ID));

        verify(songRepository, never()).findByIdAndReleaseId(anyLong(), anyLong());
    }
}