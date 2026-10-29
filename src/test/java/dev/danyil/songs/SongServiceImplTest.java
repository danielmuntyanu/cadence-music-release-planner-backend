package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.danyil.enums.Genre;
import dev.danyil.enums.SongStatus;
import dev.danyil.mappers.SongMapper;
import dev.danyil.media.ImageUploadService;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.releases.ReleaseFinder;
import dev.danyil.releases.ReleaseRepository;
import dev.danyil.releases.exceptions.ReleaseNotFoundException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;
import dev.danyil.songs.exceptions.InvalidSongOrderException;
import dev.danyil.songs.exceptions.SongNotFoundException;
import dev.danyil.users.UserEntity;

@ExtendWith(MockitoExtension.class)
class SongServiceImplTest {

    // Ids are deliberately outside the Long cache range (-128..127),
    // so an accidental reference comparison between ids fails these tests.
    private static final long USER_ID = 1000L;
    private static final long OTHER_USER_ID = 2000L;
    private static final long RELEASE_ID = 5100L;
    private static final long INTRO_ID = 7100L;
    private static final long TITLE_ID = 7200L;
    private static final long OUTRO_ID = 7300L;
    private static final long NEW_SONG_ID = 7400L;

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private SongRepository songRepository;

    @Mock 
    private ImageUploadService mediaCleanup;

    // Real finder and mapper: ownership and mapping are part of what is tested
    private SongServiceImpl songService;

    private ReleaseEntity release;
    private SongEntity intro;
    private SongEntity title;
    private SongEntity outro;

    @BeforeEach
    void setUp() {
        ReleaseFinder releaseFinder = new ReleaseFinder(releaseRepository);
        SongFinder songFinder = new SongFinder(releaseFinder, songRepository);
        songService = new SongServiceImpl(releaseFinder, songFinder, songRepository, new SongMapper(), mediaCleanup);

        UserEntity owner = new UserEntity();
        owner.setId(USER_ID);

        release = ReleaseEntity.builder().name("Night Drive").primaryGenre(Genre.POP).build();
        release.setId(RELEASE_ID);
        release.setUser(owner);

        intro = addSong(INTRO_ID, "Intro");
        title = addSong(TITLE_ID, "Night Drive");
        outro = addSong(OUTRO_ID, "Outro");
    }

    // ---------- Helpers ----------

    private SongEntity addSong(long id, String songTitle) {
        SongEntity song = SongEntity.builder().title(songTitle).build();
        song.setId(id);
        return release.addSong(song);
    }

    private static SongRequestDTO request(String songTitle) {
        return new SongRequestDTO(songTitle, 215, "us-abc-26-00001", SongStatus.RECORDING, "Lyrics", true);
    }

    private List<String> titles() {
        return release.getSongs().stream().map(SongEntity::getTitle).toList();
    }

    private void givenReleaseExists() {
        when(releaseRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release));
    }

    private void givenSongInRelease(SongEntity song) {
        when(songRepository.findByIdAndReleaseId(song.getId(), RELEASE_ID)).thenReturn(Optional.of(song));
    }

    // ---------- getAll ----------

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        void getAll_returnsTheTracklistInOrder_withTrackNumbers() {
            givenReleaseExists();

            List<SongResponseDTO> songs = songService.getAll(USER_ID, RELEASE_ID);

            assertThat(songs.stream().map(SongResponseDTO::title).toList(), contains("Intro", "Night Drive", "Outro"));
            assertThat(songs.stream().map(SongResponseDTO::trackNumber).toList(), contains(1, 2, 3));
        }

        @Test
        void getAll_otherUser_throwsOwnership() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> songService.getAll(OTHER_USER_ID, RELEASE_ID));
        }

        @Test
        void getAll_missingRelease_throwsNotFound() {
            when(releaseRepository.findById(RELEASE_ID)).thenReturn(Optional.empty());

            assertThrows(ReleaseNotFoundException.class, () -> songService.getAll(USER_ID, RELEASE_ID));
        }
    }

    // ---------- getById ----------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void getById_songOfThisRelease_isReturned() {
            givenReleaseExists();
            givenSongInRelease(title);

            SongResponseDTO song = songService.getById(USER_ID, RELEASE_ID, TITLE_ID);

            assertThat(song.title(), is("Night Drive"));
            assertThat(song.trackNumber(), is(2));
        }

        @Test
        void getById_songOfAnotherRelease_throwsNotFound() {
            // The repository is scoped to the release in the URL, so a foreign song is simply absent
            givenReleaseExists();
            when(songRepository.findByIdAndReleaseId(9999L, RELEASE_ID)).thenReturn(Optional.empty());

            assertThrows(SongNotFoundException.class, () -> songService.getById(USER_ID, RELEASE_ID, 9999L));
        }

        @Test
        void getById_otherUser_isRejectedBeforeLookingForTheSong() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> songService.getById(OTHER_USER_ID, RELEASE_ID, TITLE_ID));

            verify(songRepository, never()).findByIdAndReleaseId(anyLong(), anyLong());
        }
    }

    // ---------- create ----------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void create_appendsAtTheEnd_andReturnsTheSavedSong() {
            givenReleaseExists();
            when(songRepository.save(any(SongEntity.class))).thenAnswer(invocation -> {
                SongEntity saved = invocation.getArgument(0);
                saved.setId(NEW_SONG_ID);
                return saved;
            });

            SongResponseDTO response = songService.create(USER_ID, RELEASE_ID, request("  Bonus Track "));

            ArgumentCaptor<SongEntity> captor = ArgumentCaptor.forClass(SongEntity.class);
            verify(songRepository).save(captor.capture());
            SongEntity saved = captor.getValue();

            assertThat(saved.getRelease(), is(sameInstance(release)));
            assertThat(saved.getPosition(), is(3));
            assertThat(saved.getIsrc(), is("USABC2600001"));
            assertThat(titles(), contains("Intro", "Night Drive", "Outro", "Bonus Track"));

            assertThat(response.id(), is(NEW_SONG_ID));
            assertThat(response.trackNumber(), is(4));
            assertThat(response.title(), is("Bonus Track"));
        }

        @Test
        void create_otherUser_throwsAndSavesNothing() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> songService.create(OTHER_USER_ID, RELEASE_ID, request("Bonus Track")));

            verify(songRepository, never()).save(any());
            assertThat(titles(), contains("Intro", "Night Drive", "Outro"));
        }
    }

    // ---------- replace and status ----------

    @Nested
    @DisplayName("replace and status")
    class ReplaceAndStatus {

        @Test
        void replace_setsEveryField_andNullsClearOptionalOnes() {
            title.setDurationSeconds(215);
            title.setIsrc("USABC2600001");
            title.setLyrics("Old lyrics");
            givenReleaseExists();
            givenSongInRelease(title);

            SongResponseDTO response = songService.replace(USER_ID, RELEASE_ID, TITLE_ID,
                new SongRequestDTO("Night Drive (Edit)", null, null, SongStatus.MIXING_MASTERING, null, false));

            assertThat(title.getTitle(), is("Night Drive (Edit)"));
            assertThat(title.getDurationSeconds(), is(nullValue()));
            assertThat(title.getIsrc(), is(nullValue()));
            assertThat(title.getLyrics(), is(nullValue()));
            assertThat(title.getStatus(), is(SongStatus.MIXING_MASTERING));
            assertThat(response.trackNumber(), is(2));
        }

        @Test
        void replace_doesNotMoveTheSong() {
            givenReleaseExists();
            givenSongInRelease(outro);

            songService.replace(USER_ID, RELEASE_ID, OUTRO_ID, request("Outro (Extended)"));

            assertThat(outro.getPosition(), is(2));
        }

        @Test
        void updateStatus_changesOnlyTheStatus() {
            givenReleaseExists();
            givenSongInRelease(intro);

            SongResponseDTO response = songService.updateStatus(USER_ID, RELEASE_ID, INTRO_ID,
                new SongStatusUpdateRequestDTO(SongStatus.DONE));

            assertThat(intro.getStatus(), is(SongStatus.DONE));
            assertThat(intro.getTitle(), is("Intro"));
            assertThat(response.status(), is(SongStatus.DONE));
        }

        @Test
        void updateStatus_songOfAnotherRelease_throwsNotFound() {
            givenReleaseExists();
            when(songRepository.findByIdAndReleaseId(9999L, RELEASE_ID)).thenReturn(Optional.empty());

            assertThrows(SongNotFoundException.class, () -> songService.updateStatus(
                USER_ID, RELEASE_ID, 9999L, new SongStatusUpdateRequestDTO(SongStatus.DONE)));
        }
    }

    // ---------- delete ----------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void delete_removesTheSong_andClosesTheGap() {
            givenReleaseExists();
            givenSongInRelease(title);

            songService.delete(USER_ID, RELEASE_ID, TITLE_ID);

            assertThat(titles(), contains("Intro", "Outro"));
            assertThat(outro.getPosition(), is(1));
        }

        @Test
        void delete_otherUser_throwsAndKeepsTheTracklist() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> songService.delete(OTHER_USER_ID, RELEASE_ID, TITLE_ID));

            assertThat(titles(), contains("Intro", "Night Drive", "Outro"));
        }

        @Test
        void delete_songOfAnotherRelease_throwsAndKeepsTheTracklist() {
            givenReleaseExists();
            when(songRepository.findByIdAndReleaseId(9999L, RELEASE_ID)).thenReturn(Optional.empty());

            assertThrows(SongNotFoundException.class, () -> songService.delete(USER_ID, RELEASE_ID, 9999L));

            assertThat(titles(), contains("Intro", "Night Drive", "Outro"));
        }
    }

    // ---------- reorder ----------

    @Nested
    @DisplayName("reorder")
    class Reorder {

        @Test
        void reorder_appliesTheOrder_andReturnsTheNewTracklist() {
            givenReleaseExists();

            List<SongResponseDTO> songs = songService.reorder(USER_ID, RELEASE_ID,
                new SongOrderRequestDTO(List.of(OUTRO_ID, INTRO_ID, TITLE_ID)));

            assertThat(songs.stream().map(SongResponseDTO::title).toList(), contains("Outro", "Intro", "Night Drive"));
            assertThat(songs.stream().map(SongResponseDTO::trackNumber).toList(), contains(1, 2, 3));
        }

        @Test
        void reorder_staleList_throwsAndKeepsTheOrder() {
            givenReleaseExists();

            assertThrows(InvalidSongOrderException.class,
                () -> songService.reorder(USER_ID, RELEASE_ID, new SongOrderRequestDTO(List.of(OUTRO_ID, INTRO_ID))));

            assertThat(titles(), contains("Intro", "Night Drive", "Outro"));
        }

        @Test
        void reorder_otherUser_throwsOwnership() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> songService.reorder(OTHER_USER_ID, RELEASE_ID,
                new SongOrderRequestDTO(List.of(OUTRO_ID, INTRO_ID, TITLE_ID))));
        }
    }
}