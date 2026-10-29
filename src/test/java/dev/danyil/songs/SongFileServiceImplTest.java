package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.danyil.contracts.StorageService;
import dev.danyil.mappers.SongFileMapper;
import dev.danyil.media.ImageUploadService;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.songs.dtos.SongFileConfirmRequestDTO;
import dev.danyil.songs.dtos.SongFileRenameRequestDTO;
import dev.danyil.songs.dtos.SongFileResponseDTO;
import dev.danyil.songs.dtos.SongFileUploadRequestDTO;
import dev.danyil.songs.dtos.SongFileUploadResponseDTO;
import dev.danyil.songs.dtos.SongPrimaryFileRequestDTO;
import dev.danyil.songs.exceptions.InvalidAudioFileException;
import dev.danyil.songs.exceptions.SongFileNotFoundException;
import dev.danyil.songs.exceptions.UploadNotFoundException;
import dev.danyil.storage.StorageProperties;
import dev.danyil.storage.dtos.DownloadLinkDTO;
import dev.danyil.storage.dtos.PresignedUpload;
import dev.danyil.storage.dtos.StoredObjectInfo;

@ExtendWith(MockitoExtension.class)
class SongFileServiceImplTest {

    // Ids are deliberately outside the Long cache range (-128..127),
    // so an accidental reference comparison between ids fails these tests.
    private static final long USER_ID = 1000L;
    private static final long RELEASE_ID = 5100L;
    private static final long SONG_ID = 7100L;
    private static final long DEMO_ID = 8100L;
    private static final long MIX_ID = 8200L;
    private static final long NEW_FILE_ID = 8300L;

    // Small limit, so "too large" is easy to reach in tests
    private static final long MAX_BYTES = 1_000L;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

    private static final String OWN_PREFIX = "users/" + USER_ID + "/song-files/";
    private static final String NEW_KEY = OWN_PREFIX + "new-upload.wav";

    @Mock private SongFinder songFinder;
    @Mock private SongFileRepository songFileRepository;
    @Mock private StorageService storage;
    @Mock private ImageUploadService mediaCleanup;
    @Mock private StorageProperties storageProperties;

    private SongFileServiceImpl service;
    private SongEntity song;
    private SongFileEntity demo;
    private SongFileEntity mix;

    @BeforeEach
    void setUp() {
        service = new SongFileServiceImpl(
            songFinder, songFileRepository, storage, mediaCleanup,
            new SongFileMapper(storage), new AudioUploadProperties(MAX_BYTES, UPLOAD_TTL), storageProperties);

        song = SongEntity.builder().title("Night Drive").build();
        demo = song.addFile(file(DEMO_ID, OWN_PREFIX + "demo.wav", "demo"));
        mix = song.addFile(file(MIX_ID, OWN_PREFIX + "mix.wav", "Night Drive mix3"));
    }

    // ---------- Helpers ----------

    private static SongFileEntity file(long id, String key, String name) {
        SongFileEntity file = SongFileEntity.builder()
            .storageKey(key).displayName(name).originalFilename(name + ".wav")
            .contentType("audio/wav").sizeBytes(500).build();
        file.setId(id);
        return file;
    }

    private void givenOwnSong() {
        when(songFinder.findOwned(USER_ID, RELEASE_ID, SONG_ID)).thenReturn(song);
    }

    private void givenNotOwned() {
        when(songFinder.findOwned(USER_ID, RELEASE_ID, SONG_ID)).thenThrow(new ReleaseOwnershipException());
    }

    private void givenSaveAssignsAnId() {
        when(songFileRepository.save(any(SongFileEntity.class))).thenAnswer(invocation -> {
            SongFileEntity saved = invocation.getArgument(0);
            saved.setId(NEW_FILE_ID);
            return saved;
        });
    }

    private static List<String> names(List<SongFileResponseDTO> files) {
        return files.stream().map(SongFileResponseDTO::displayName).toList();
    }

    // ---------- getAll ----------

    @Test
    void getAll_listsTheNewestFirst_withTheHeart() {
        givenOwnSong();

        List<SongFileResponseDTO> files = service.getAll(USER_ID, RELEASE_ID, SONG_ID);

        assertThat(names(files), contains("Night Drive mix3", "demo"));
        assertThat(files.get(1).primary(), is(true));
    }

    // ---------- startUpload ----------

    @Nested
    @DisplayName("startUpload")
    class StartUpload {

        @Test
        void signsAPutForThisUser_withTheCanonicalTypeAndExactSize() {
            givenOwnSong();
            when(storage.presignPut(anyString(), eq("audio/wav"), eq(800L), eq(UPLOAD_TTL)))
                .thenReturn(new PresignedUpload("https://storage.test/put", Instant.now(), Map.of("content-type", "audio/wav")));

            // Upper-case extension: the canonical type is still audio/wav
            SongFileUploadResponseDTO response = service.startUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileUploadRequestDTO("Night Drive MIX3.WAV", 800));

            assertThat(response.key(), startsWith(OWN_PREFIX));
            assertThat(response.key().endsWith(".wav"), is(true));
            assertThat(response.uploadUrl(), is("https://storage.test/put"));
            assertThat(response.headers(), is(Map.of("content-type", "audio/wav")));
        }

        @Test
        void givesEveryUploadItsOwnKey() {
            givenOwnSong();
            when(storage.presignPut(anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PresignedUpload("https://storage.test/put", Instant.now(), Map.of()));

            String first = service.startUpload(USER_ID, RELEASE_ID, SONG_ID, new SongFileUploadRequestDTO("a.wav", 1)).key();
            String second = service.startUpload(USER_ID, RELEASE_ID, SONG_ID, new SongFileUploadRequestDTO("a.wav", 1)).key();

            assertThat(first.equals(second), is(false));
        }

        @Test
        void rejectsAFileThatIsNotAudio_beforeSigningAnything() {
            givenOwnSong();

            assertThrows(InvalidAudioFileException.class, () -> service.startUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileUploadRequestDTO("cover.png", 800)));

            verify(storage, never()).presignPut(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void rejectsAFileOverTheLimit() {
            givenOwnSong();

            assertThrows(InvalidAudioFileException.class, () -> service.startUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileUploadRequestDTO("master.wav", MAX_BYTES + 1)));

            verify(storage, never()).presignPut(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void acceptsAFileExactlyAtTheLimit() {
            givenOwnSong();
            when(storage.presignPut(anyString(), anyString(), eq(MAX_BYTES), any()))
                .thenReturn(new PresignedUpload("https://storage.test/put", Instant.now(), Map.of()));

            service.startUpload(USER_ID, RELEASE_ID, SONG_ID, new SongFileUploadRequestDTO("master.wav", MAX_BYTES));

            verify(storage).presignPut(anyString(), anyString(), eq(MAX_BYTES), any());
        }

        @Test
        void aSongOfAnotherUser_getsNoSignedUrl() {
            givenNotOwned();

            assertThrows(ReleaseOwnershipException.class, () -> service.startUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileUploadRequestDTO("mix.wav", 800)));

            verify(storage, never()).presignPut(anyString(), anyString(), anyLong(), any());
        }
    }

    // ---------- confirmUpload ----------

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUpload {

        @Test
        void createsTheFile_withTheSizeReportedByStorage() {
            givenOwnSong();
            when(storage.describe(NEW_KEY)).thenReturn(Optional.of(new StoredObjectInfo(800, "application/octet-stream")));
            givenSaveAssignsAnId();

            SongFileResponseDTO response = service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "Night Drive master_v_5.wav", "  Master v5  "));

            ArgumentCaptor<SongFileEntity> captor = ArgumentCaptor.forClass(SongFileEntity.class);
            verify(songFileRepository).save(captor.capture());
            SongFileEntity saved = captor.getValue();

            assertThat(saved.getSong(), is(sameInstance(song)));
            assertThat(saved.getStorageKey(), is(NEW_KEY));
            assertThat(saved.getDisplayName(), is("Master v5"));
            assertThat(saved.getOriginalFilename(), is("Night Drive master_v_5.wav"));
            // Canonical type from the extension, not whatever storage recorded
            assertThat(saved.getContentType(), is("audio/wav"));
            assertThat(saved.getSizeBytes(), is(800L));
            assertThat(response.id(), is(NEW_FILE_ID));
            assertThat(song.getFiles().get(0), is(sameInstance(saved)));
        }

        @Test
        void blankName_fallsBackToTheFileName() {
            givenOwnSong();
            when(storage.describe(NEW_KEY)).thenReturn(Optional.of(new StoredObjectInfo(800, "audio/wav")));
            givenSaveAssignsAnId();

            SongFileResponseDTO response = service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "I Belong To You mix3.wav", "   "));

            assertThat(response.displayName(), is("I Belong To You mix3"));
        }

        @Test
        void aVeryLongFileName_isCutToTheNameLimit() {
            givenOwnSong();
            when(storage.describe(NEW_KEY)).thenReturn(Optional.of(new StoredObjectInfo(800, "audio/wav")));
            givenSaveAssignsAnId();

            SongFileResponseDTO response = service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "x".repeat(200) + ".wav", null));

            assertThat(response.displayName().length(), is(150));
        }

        @Test
        void theFirstFileOfASong_becomesPrimary() {
            SongEntity empty = SongEntity.builder().title("Intro").build();
            when(songFinder.findOwned(USER_ID, RELEASE_ID, SONG_ID)).thenReturn(empty);
            when(storage.describe(NEW_KEY)).thenReturn(Optional.of(new StoredObjectInfo(800, "audio/wav")));
            givenSaveAssignsAnId();

            SongFileResponseDTO response = service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "demo.wav", null));

            assertThat(response.primary(), is(true));
        }

        @Test
        void anotherUsersKey_isRejected_withoutAskingStorage() {
            // Accepting it would hand out a signed link to someone else's audio
            givenOwnSong();

            assertThrows(UploadNotFoundException.class, () -> service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO("users/2000/song-files/theirs.wav", "theirs.wav", null)));

            verify(storage, never()).describe(anyString());
            verify(songFileRepository, never()).save(any());
        }

        @Test
        void aKeyThatIsAlreadyUsed_isRejected() {
            givenOwnSong();
            when(songFileRepository.existsByStorageKey(NEW_KEY)).thenReturn(true);

            assertThrows(UploadNotFoundException.class, () -> service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "mix.wav", null)));

            verify(songFileRepository, never()).save(any());
        }

        @Test
        void anUploadThatNeverReachedStorage_isRejected() {
            givenOwnSong();
            when(storage.describe(NEW_KEY)).thenReturn(Optional.empty());

            assertThrows(UploadNotFoundException.class, () -> service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "mix.wav", null)));

            verify(songFileRepository, never()).save(any());
        }

        @Test
        void anObjectLargerThanTheLimit_isRejected() {
            givenOwnSong();
            when(storage.describe(NEW_KEY)).thenReturn(Optional.of(new StoredObjectInfo(MAX_BYTES + 1, "audio/wav")));

            assertThrows(InvalidAudioFileException.class, () -> service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(NEW_KEY, "mix.wav", null)));

            verify(songFileRepository, never()).save(any());
        }

        @Test
        void aKeyWithoutAnAudioExtension_isRejected() {
            givenOwnSong();

            assertThrows(InvalidAudioFileException.class, () -> service.confirmUpload(USER_ID, RELEASE_ID, SONG_ID,
                new SongFileConfirmRequestDTO(OWN_PREFIX + "page.html", "page.html", null)));

            verify(songFileRepository, never()).save(any());
        }
    }

    // ---------- rename, heart, download ----------

    @Nested
    @DisplayName("rename, primary and download")
    class ChangeFiles {

        @Test
        void rename_trimsAndKeepsEverythingElse() {
            givenOwnSong();

            SongFileResponseDTO response = service.rename(USER_ID, RELEASE_ID, SONG_ID, MIX_ID,
                new SongFileRenameRequestDTO("  Mix 3 (final)  "));

            assertThat(mix.getDisplayName(), is("Mix 3 (final)"));
            assertThat(mix.getStorageKey(), is(OWN_PREFIX + "mix.wav"));
            assertThat(response.displayName(), is("Mix 3 (final)"));
        }

        @Test
        void rename_aFileOfAnotherSong_isNotFound() {
            givenOwnSong();

            assertThrows(SongFileNotFoundException.class, () -> service.rename(USER_ID, RELEASE_ID, SONG_ID, 9999L,
                new SongFileRenameRequestDTO("x")));
        }

        @Test
        void setPrimary_movesTheHeart_andReturnsTheWholeList() {
            givenOwnSong();

            List<SongFileResponseDTO> files = service.setPrimary(USER_ID, RELEASE_ID, SONG_ID,
                new SongPrimaryFileRequestDTO(MIX_ID));

            assertThat(song.getPrimaryFile(), is(sameInstance(mix)));
            assertThat(files.stream().map(SongFileResponseDTO::primary).toList(), contains(true, false));
        }

        @Test
        void setPrimary_null_clearsTheHeart() {
            givenOwnSong();

            service.setPrimary(USER_ID, RELEASE_ID, SONG_ID, new SongPrimaryFileRequestDTO(null));

            assertThat(song.getPrimaryFile(), is(nullValue()));
        }

        @Test
        void setPrimary_aFileOfAnotherSong_isNotFound_andKeepsTheHeart() {
            givenOwnSong();

            assertThrows(SongFileNotFoundException.class, () -> service.setPrimary(USER_ID, RELEASE_ID, SONG_ID,
                new SongPrimaryFileRequestDTO(9999L)));

            assertThat(song.getPrimaryFile(), is(sameInstance(demo)));
        }

        @Test
        void downloadLink_savesTheFileUnderItsDisplayName() {
            givenOwnSong();
            when(storageProperties.downloadUrlTtl()).thenReturn(DOWNLOAD_TTL);
            when(storage.presignDownload(OWN_PREFIX + "mix.wav", "Night Drive mix3.wav", DOWNLOAD_TTL))
                .thenReturn("https://storage.test/download");

            DownloadLinkDTO link = service.downloadLink(USER_ID, RELEASE_ID, SONG_ID, MIX_ID);

            assertThat(link.url(), is("https://storage.test/download"));
            assertThat(link.expiresAt().isAfter(Instant.now()), is(true));
            assertThat(Duration.between(Instant.now(), link.expiresAt()).toMinutes(), greaterThan(3L));
        }
    }

    // ---------- delete ----------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void removesTheRow_andTheAudioAfterCommit() {
            givenOwnSong();

            service.delete(USER_ID, RELEASE_ID, SONG_ID, MIX_ID);

            assertThat(song.getFiles(), contains(demo));
            verify(mediaCleanup).deleteAfterCommit(OWN_PREFIX + "mix.wav");
        }

        @Test
        void deletingThePrimaryFile_leavesNoPrimary() {
            givenOwnSong();

            service.delete(USER_ID, RELEASE_ID, SONG_ID, DEMO_ID);

            assertThat(song.getPrimaryFile(), is(nullValue()));
        }

        @Test
        void aFileOfAnotherSong_isNotFound_andNothingIsDeleted() {
            givenOwnSong();

            assertThrows(SongFileNotFoundException.class, () -> service.delete(USER_ID, RELEASE_ID, SONG_ID, 9999L));

            verify(mediaCleanup, never()).deleteAfterCommit(anyString());
            assertThat(song.getFiles().size(), is(2));
        }
    }
}