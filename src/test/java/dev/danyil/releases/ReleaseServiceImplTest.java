package dev.danyil.releases;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.artists.ArtistRepository;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.contracts.StorageService;
import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import dev.danyil.mappers.ReleaseMapper;
import dev.danyil.media.ImageKind;
import dev.danyil.media.ImageUploadService;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseDatesRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleaseGenresRequestDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import dev.danyil.releases.exceptions.ArtworkNotFoundException;
import dev.danyil.releases.exceptions.ReleaseAlreadyExistsException;
import dev.danyil.releases.exceptions.ReleaseException;
import dev.danyil.releases.exceptions.ReleaseNotFoundException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.songs.SongEntity;
import dev.danyil.songs.SongFileEntity;
import dev.danyil.storage.StorageProperties;
import dev.danyil.storage.dtos.DownloadLinkDTO;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceImplTest {

    // Ids are deliberately outside the Long cache range (-128..127),
    // so an accidental reference comparison between ids fails these tests.
    private static final long USER_ID = 1000L;
    private static final long OTHER_USER_ID = 2000L;
    private static final long RELEASE_ID = 5100L;
    private static final long NOVA_ID = 4200L;
    private static final long ADA_ID = 4300L;
    private static final long MISSING_ARTIST_ID = 9900L;

    private static final ReleaseFilterDTO NO_FILTER = new ReleaseFilterDTO(null, null, null, null, null);

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageUploadService imageUploads;

    @Mock 
    private StorageService storage;

    @Mock 
    private StorageProperties storageProperties;

    // Real mapper with a fake signer: responses show which URLs were signed
    @Spy
    private ReleaseMapper releaseMapper = new ReleaseMapper(key -> "signed:" + key);

    // Built by hand in setUp: the real ReleaseFinder needs the mocked repository,
    // which does not exist yet when field initializers run
    private ReleaseServiceImpl releaseService;

    private UserEntity owner;
    private ArtistEntity nova;
    private ArtistEntity ada;
    private ReleaseEntity release;

    @BeforeEach
    void setUp() {
        ReleaseFinder releaseFinder = new ReleaseFinder(releaseRepository);
        // Argument order must match the final fields of ReleaseServiceImpl
        releaseService = new ReleaseServiceImpl(
            releaseMapper, releaseRepository, artistRepository, userRepository, imageUploads, releaseFinder,
            storage, storageProperties);

        owner = new UserEntity();
        owner.setId(USER_ID);

        nova = artist(NOVA_ID, "Nova");
        ada = artist(ADA_ID, "Ada");

        release = ReleaseEntity.builder()
            .name("Night Drive")
            .type(ReleaseType.EP)
            .notes("Mix notes")
            .primaryGenre(Genre.POP)
            .releaseDate(LocalDate.of(2026, 11, 14))
            .build();
        release.setId(RELEASE_ID);
        release.setUser(owner);
        release.replaceArtists(List.of(nova));
    }

    // ---------- Test data helpers ----------

    private ArtistEntity artist(long id, String name) {
        ArtistEntity artist = new ArtistEntity();
        artist.setId(id);
        artist.setName(name);
        artist.setUser(owner);
        return artist;
    }

    private static ReleaseCreateRequestDTO createRequest(String name, List<Long> artistIds) {
        return new ReleaseCreateRequestDTO(
            name, ReleaseType.EP, null, null, Genre.POP, null, null, null, artistIds);
    }

    private static ReleaseUpdateRequestDTO updateName(String name) {
        return new ReleaseUpdateRequestDTO(name, null, null, null);
    }

    private static ReleaseUpdateRequestDTO updateNotes(String notes) {
        return new ReleaseUpdateRequestDTO(null, null, notes, null);
    }

    private static ReleaseUpdateRequestDTO updateArtists(List<Long> artistIds) {
        return new ReleaseUpdateRequestDTO(null, null, null, artistIds);
    }

    private static List<String> artistNames(ReleaseEntity entity) {
        return entity.getArtists().stream().map(link -> link.getArtist().getName()).toList();
    }

    @SuppressWarnings("unchecked")
    private static Specification<ReleaseEntity> anySpecification() {
        // Typed matcher: findAll(Specification, Pageable) vs findAll(Example, Pageable) would be ambiguous
        return any(Specification.class);
    }

    private void givenReleaseExists() {
        when(releaseRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release));
    }

    private void givenReleaseDoesNotExist() {
        when(releaseRepository.findById(RELEASE_ID)).thenReturn(Optional.empty());
    }

    // ---------- create ----------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void create_validRequest_savesTrimmedReleaseOwnedByUserWithArtistsInRequestOrder() {
            when(releaseRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Night Drive")).thenReturn(false);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
            // The database returns artists in its own order, not in the requested one
            when(artistRepository.findAllByIdInAndUserId(List.of(ADA_ID, NOVA_ID), USER_ID))
                .thenReturn(List.of(nova, ada));
            when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(invocation -> {
                ReleaseEntity toSave = invocation.getArgument(0);
                toSave.setId(RELEASE_ID);
                return toSave;
            });

            ReleaseResponseDTO response = releaseService.create(
                createRequest("  Night Drive  ", List.of(ADA_ID, NOVA_ID)), USER_ID);

            ArgumentCaptor<ReleaseEntity> captor = ArgumentCaptor.forClass(ReleaseEntity.class);
            verify(releaseRepository).save(captor.capture());
            ReleaseEntity saved = captor.getValue();

            assertThat(saved.getName(), is("Night Drive"));
            assertThat(saved.getPhase(), is(ReleasePhase.PLANNING));
            assertThat(saved.getUser(), is(sameInstance(owner)));
            assertThat(artistNames(saved), contains("Ada", "Nova"));

            assertThat(response.id(), is(RELEASE_ID));
            assertThat(response.artists().get(0).name(), is("Ada"));
        }

        @Test
        void create_withoutArtists_doesNotQueryArtists() {
            when(releaseRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Night Drive")).thenReturn(false);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
            when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReleaseResponseDTO response = releaseService.create(createRequest("Night Drive", null), USER_ID);

            assertThat(response.artists(), is(empty()));
            verify(artistRepository, never()).findAllByIdInAndUserId(anyCollection(), anyLong());
        }

        @Test
        void create_nameAlreadyUsedByUser_throwsAndDoesNotSave() {
            when(releaseRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Night Drive")).thenReturn(true);

            assertThrows(ReleaseAlreadyExistsException.class,
                () -> releaseService.create(createRequest("Night Drive", null), USER_ID));

            verify(releaseRepository, never()).save(any());
        }

        @Test
        void create_artistMissingOrOwnedByAnotherUser_throwsAndDoesNotSave() {
            when(releaseRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Night Drive")).thenReturn(false);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
            // The query is scoped to the user, so another user's artist simply is not returned
            when(artistRepository.findAllByIdInAndUserId(List.of(NOVA_ID, MISSING_ARTIST_ID), USER_ID))
                .thenReturn(List.of(nova));

            assertThrows(ArtistNotFoundException.class,
                () -> releaseService.create(createRequest("Night Drive", List.of(NOVA_ID, MISSING_ARTIST_ID)), USER_ID));

            verify(releaseRepository, never()).save(any());
        }
    }

    // ---------- getAll ----------

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        void getAll_returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 24, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(releaseRepository.findAll(anySpecification(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(release), pageable, 1));

            Page<ReleaseResponseDTO> page = releaseService.getAll(USER_ID, NO_FILTER, pageable);

            assertThat(page.getTotalElements(), is(1L));
            assertThat(page.getContent().get(0).name(), is("Night Drive"));
            assertThat(page.getContent().get(0).artists(), hasSize(1));
        }

        @ParameterizedTest
        @ValueSource(strings = { "name", "releaseDate", "createdAt" })
        void getAll_allowedSortField_queriesRepository(String field) {
            Pageable pageable = PageRequest.of(0, 24, Sort.by(field));
            when(releaseRepository.findAll(anySpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

            releaseService.getAll(USER_ID, NO_FILTER, pageable);

            verify(releaseRepository).findAll(anySpecification(), eq(pageable));
        }

        @ParameterizedTest
        @ValueSource(strings = { "user.password", "notes", "id", "phase", "type" })
        void getAll_sortFieldNotAllowed_throwsBadRequestAndDoesNotQuery(String field) {
            Pageable pageable = PageRequest.of(0, 24, Sort.by(field));

            assertThrows(ReleaseException.class,
                () -> releaseService.getAll(USER_ID, NO_FILTER, pageable));

            verify(releaseRepository, never()).findAll(anySpecification(), any(Pageable.class));
        }
    }

    // ---------- getById ----------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void getById_ownerRequests_returnsRelease() {
            givenReleaseExists();

            ReleaseResponseDTO response = releaseService.getById(USER_ID, RELEASE_ID);

            assertThat(response.id(), is(RELEASE_ID));
            assertThat(response.artists().get(0).name(), is("Nova"));
        }

        @Test
        void getById_releaseDoesNotExist_throwsNotFound() {
            givenReleaseDoesNotExist();

            assertThrows(ReleaseNotFoundException.class, () -> releaseService.getById(USER_ID, RELEASE_ID));
        }

        @Test
        void getById_otherUserRequests_throwsOwnership() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> releaseService.getById(OTHER_USER_ID, RELEASE_ID));
        }
    }

    // ---------- update ----------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void update_onlyNotesProvided_changesNotesAndKeepsOtherFields() {
            givenReleaseExists();

            releaseService.update(USER_ID, RELEASE_ID, updateNotes("Final master approved"));

            assertThat(release.getNotes(), is("Final master approved"));
            assertThat(release.getName(), is("Night Drive"));
            assertThat(release.getPrimaryGenre(), is(Genre.POP));
            assertThat(artistNames(release), contains("Nova"));
            verify(artistRepository, never()).findAllByIdInAndUserId(anyCollection(), anyLong());
        }

        @Test
        void update_emptyNotes_clearsField() {
            givenReleaseExists();

            releaseService.update(USER_ID, RELEASE_ID, updateNotes(""));

            assertThat(release.getNotes(), is(nullValue()));
        }

        @Test
        void update_artistIdsInNewOrder_reordersArtists() {
            givenReleaseExists();
            when(artistRepository.findAllByIdInAndUserId(List.of(ADA_ID, NOVA_ID), USER_ID))
                .thenReturn(List.of(nova, ada));

            releaseService.update(USER_ID, RELEASE_ID, updateArtists(List.of(ADA_ID, NOVA_ID)));

            assertThat(artistNames(release), contains("Ada", "Nova"));
        }

        @Test
        void update_emptyArtistIds_removesAllArtists() {
            givenReleaseExists();

            releaseService.update(USER_ID, RELEASE_ID, updateArtists(List.of()));

            assertThat(release.getArtists(), is(empty()));
        }

        @Test
        void update_newNameTakenByAnotherRelease_throwsAndKeepsOldName() {
            givenReleaseExists();
            when(releaseRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Echoes", RELEASE_ID))
                .thenReturn(true);

            assertThrows(ReleaseAlreadyExistsException.class,
                () -> releaseService.update(USER_ID, RELEASE_ID, updateName("Echoes")));

            assertThat(release.getName(), is("Night Drive"));
        }

        @Test
        void update_ownNameDifferentCase_isAllowed() {
            givenReleaseExists();

            releaseService.update(USER_ID, RELEASE_ID, updateName("NIGHT DRIVE"));

            assertThat(release.getName(), is("NIGHT DRIVE"));
            // The uniqueness check excludes the release itself
            verify(releaseRepository).existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "NIGHT DRIVE", RELEASE_ID);
        }

        @Test
        void update_otherUserRequests_throwsAndLeavesReleaseUnchanged() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> releaseService.update(OTHER_USER_ID, RELEASE_ID, updateName("Hacked")));

            assertThat(release.getName(), is("Night Drive"));
            verify(releaseMapper, never()).applyUpdate(any(), any());
        }

        @Test
        void update_releaseDoesNotExist_throwsNotFound() {
            givenReleaseDoesNotExist();

            assertThrows(ReleaseNotFoundException.class,
                () -> releaseService.update(USER_ID, RELEASE_ID, updateNotes("x")));
        }
    }

    // ---------- updatePhase ----------

    @Nested
    @DisplayName("updatePhase")
    class UpdatePhase {

        @Test
        void updatePhase_ownerRequests_changesPhase() {
            givenReleaseExists();

            ReleaseResponseDTO response = releaseService.updatePhase(
                USER_ID, RELEASE_ID, new ReleasePhaseUpdateRequestDTO(ReleasePhase.PROMO));

            assertThat(release.getPhase(), is(ReleasePhase.PROMO));
            assertThat(response.phase(), is(ReleasePhase.PROMO));
        }

        @Test
        void updatePhase_otherUserRequests_throwsAndKeepsPhase() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> releaseService.updatePhase(
                    OTHER_USER_ID, RELEASE_ID, new ReleasePhaseUpdateRequestDTO(ReleasePhase.PROMO)));

            assertThat(release.getPhase(), is(ReleasePhase.PLANNING));
        }
    }

    // ---------- delete ----------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void delete_ownerRequests_deletesRelease() {
            givenReleaseExists();

            releaseService.delete(USER_ID, RELEASE_ID);

            verify(releaseRepository).delete(release);
        }

        @Test
        void delete_otherUserRequests_throwsAndDoesNotDelete() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> releaseService.delete(OTHER_USER_ID, RELEASE_ID));

            verify(releaseRepository, never()).delete(any(ReleaseEntity.class));
        }

        @Test
        void delete_releaseDoesNotExist_throwsAndDoesNotDelete() {
            givenReleaseDoesNotExist();

            assertThrows(ReleaseNotFoundException.class, () -> releaseService.delete(USER_ID, RELEASE_ID));

            verify(releaseRepository, never()).delete(any(ReleaseEntity.class));
        }
    }


    // ---------- artwork ----------

    @Nested
    @DisplayName("artwork")
    class Artwork {

        private final MockMultipartFile file =
            new MockMultipartFile("file", "cover.png", "image/png", new byte[] { 1, 2, 3 });

        @Test
        void uploadArtwork_storesNewFile_andDeletesTheOldOneAfterCommit() {
            release.setArtworkKey("users/1000/releases/5100/artwork-old.jpg");
            givenReleaseExists();
            when(imageUploads.store("users/1000/releases/5100/artwork", ImageKind.ARTWORK, file))
                .thenReturn("users/1000/releases/5100/artwork-new.jpg");

            ReleaseResponseDTO response = releaseService.uploadArtwork(USER_ID, RELEASE_ID, file);

            verify(imageUploads).deleteAfterCommit("users/1000/releases/5100/artwork-old.jpg");
            assertThat(release.getArtworkKey(), is("users/1000/releases/5100/artwork-new.jpg"));
            assertThat(response.artworkUrl(), is("signed:users/1000/releases/5100/artwork-new.jpg"));
        }

        @Test
        void uploadArtwork_replacesAnExternalLink() {
            release.setArtworkUrl("https://example.com/cover.jpg");
            givenReleaseExists();
            when(imageUploads.store(any(), any(), any())).thenReturn("users/1000/releases/5100/artwork-new.jpg");

            releaseService.uploadArtwork(USER_ID, RELEASE_ID, file);

            assertThat(release.getArtworkUrl(), is(nullValue()));
        }

        @Test
        void uploadArtwork_otherUser_throwsBeforeProcessingTheFile() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> releaseService.uploadArtwork(OTHER_USER_ID, RELEASE_ID, file));

            verify(imageUploads, never()).store(any(), any(), any());
        }

        @Test
        void removeArtwork_clearsBothFields_andDeletesTheFileAfterCommit() {
            release.setArtworkKey("users/1000/releases/5100/artwork-old.jpg");
            givenReleaseExists();

            ReleaseResponseDTO response = releaseService.removeArtwork(USER_ID, RELEASE_ID);

            verify(imageUploads).deleteAfterCommit("users/1000/releases/5100/artwork-old.jpg");
            assertThat(release.getArtworkKey(), is(nullValue()));
            assertThat(response.artworkUrl(), is(nullValue()));
        }

        @Test
        void delete_alsoRemovesTheArtworkFile() {
            release.setArtworkKey("users/1000/releases/5100/artwork-old.jpg");
            givenReleaseExists();

            releaseService.delete(USER_ID, RELEASE_ID);

            verify(imageUploads).deleteAfterCommit("users/1000/releases/5100/artwork-old.jpg");
        }
    }

    // ---------- genres and dates ----------

    @Nested
    @DisplayName("genres and dates")
    class GenresAndDates {

        @Test
        void updateGenres_replacesBothGenres() {
            release.setSecondaryGenre(Genre.ROCK);
            givenReleaseExists();

            ReleaseResponseDTO response = releaseService.updateGenres(
                USER_ID, RELEASE_ID, new ReleaseGenresRequestDTO(Genre.ELECTRONIC, Genre.POP));

            assertThat(release.getPrimaryGenre(), is(Genre.ELECTRONIC));
            assertThat(release.getSecondaryGenre(), is(Genre.POP));
            assertThat(response.primaryGenre(), is(Genre.ELECTRONIC));
        }

        @Test
        void updateGenres_nullSecondary_removesIt() {
            release.setSecondaryGenre(Genre.ROCK);
            givenReleaseExists();

            releaseService.updateGenres(USER_ID, RELEASE_ID, new ReleaseGenresRequestDTO(Genre.POP, null));

            assertThat(release.getSecondaryGenre(), is(nullValue()));
        }

        @Test
        void updateGenres_otherUser_throwsAndKeepsGenres() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> releaseService.updateGenres(OTHER_USER_ID, RELEASE_ID, new ReleaseGenresRequestDTO(Genre.ROCK, null)));

            assertThat(release.getPrimaryGenre(), is(Genre.POP));
        }

        @Test
        void updateDates_replacesBothDates() {
            givenReleaseExists();
            LocalDate original = LocalDate.of(2020, 5, 1);
            LocalDate reissue = LocalDate.of(2026, 12, 4);

            releaseService.updateDates(USER_ID, RELEASE_ID, new ReleaseDatesRequestDTO(reissue, original));

            assertThat(release.getReleaseDate(), is(reissue));
            assertThat(release.getOriginalReleaseDate(), is(original));
        }

        @Test
        void updateDates_nulls_clearBothDates() {
            // The reason this endpoint exists: PATCH could not express "clear this date"
            release.setOriginalReleaseDate(LocalDate.of(2020, 5, 1));
            givenReleaseExists();

            ReleaseResponseDTO response = releaseService.updateDates(USER_ID, RELEASE_ID, new ReleaseDatesRequestDTO(null, null));

            assertThat(release.getReleaseDate(), is(nullValue()));
            assertThat(release.getOriginalReleaseDate(), is(nullValue()));
            assertThat(response.releaseDate(), is(nullValue()));
        }

        @Test
        void updateDates_otherUser_throwsAndKeepsDates() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class,
                () -> releaseService.updateDates(OTHER_USER_ID, RELEASE_ID, new ReleaseDatesRequestDTO(null, null)));

            assertThat(release.getReleaseDate(), is(LocalDate.of(2026, 11, 14)));
        }

        @Test
        void updateDates_missingRelease_throwsNotFound() {
            givenReleaseDoesNotExist();

            assertThrows(ReleaseNotFoundException.class,
                () -> releaseService.updateDates(USER_ID, RELEASE_ID, new ReleaseDatesRequestDTO(null, null)));
        }
    }

    // ---------- Cover originals, downloads, and audio cleanup ----------

    @Nested
    @DisplayName("cover originals and file cleanup")
    class Files {

        @Test
        void uploadArtwork_keepsTheResizedCopyAndTheOriginal_andDropsBothOldOnes() {
            release.setArtworkKey("old-display.jpg");
            release.setArtworkOriginalKey("old-original.png");
            givenReleaseExists();
            MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[] { 1 });
            when(imageUploads.store(anyString(), eq(ImageKind.ARTWORK), eq(file))).thenReturn("new-display.jpg");
            when(imageUploads.storeOriginal(anyString(), eq(file))).thenReturn("new-original.png");

            releaseService.uploadArtwork(USER_ID, RELEASE_ID, file);

            assertThat(release.getArtworkKey(), is("new-display.jpg"));
            assertThat(release.getArtworkOriginalKey(), is("new-original.png"));
            verify(imageUploads).deleteAfterCommit("old-display.jpg");
            verify(imageUploads).deleteAfterCommit("old-original.png");
        }

        @Test
        void removeArtwork_removesBothCopies() {
            release.setArtworkKey("display.jpg");
            release.setArtworkOriginalKey("original.png");
            givenReleaseExists();

            releaseService.removeArtwork(USER_ID, RELEASE_ID);

            assertThat(release.getArtworkKey(), is(nullValue()));
            assertThat(release.getArtworkOriginalKey(), is(nullValue()));
            verify(imageUploads).deleteAfterCommit("display.jpg");
            verify(imageUploads).deleteAfterCommit("original.png");
        }

        @Test
        void delete_alsoRemovesTheAudioOfEverySong() {
            SongEntity song = release.addSong(SongEntity.builder().title("Intro").build());
            song.addFile(SongFileEntity.builder().storageKey("users/1000/song-files/a.wav").displayName("demo")
                .originalFilename("demo.wav").contentType("audio/wav").sizeBytes(1).build());
            song.addFile(SongFileEntity.builder().storageKey("users/1000/song-files/b.wav").displayName("mix")
                .originalFilename("mix.wav").contentType("audio/wav").sizeBytes(1).build());
            givenReleaseExists();

            releaseService.delete(USER_ID, RELEASE_ID);

            verify(imageUploads).deleteAfterCommit("users/1000/song-files/a.wav");
            verify(imageUploads).deleteAfterCommit("users/1000/song-files/b.wav");
            verify(releaseRepository).delete(release);
        }

        @Test
        void artworkDownloadLink_prefersTheOriginal() {
            release.setArtworkKey("display.jpg");
            release.setArtworkOriginalKey("original.png");
            givenReleaseExists();
            when(storageProperties.downloadUrlTtl()).thenReturn(Duration.ofMinutes(5));
            when(storage.presignDownload("original.png", "Night Drive - cover.png", Duration.ofMinutes(5)))
                .thenReturn("https://storage.test/cover.png");

            DownloadLinkDTO link = releaseService.artworkDownloadLink(USER_ID, RELEASE_ID);

            assertThat(link.url(), is("https://storage.test/cover.png"));
        }

        @Test
        void artworkDownloadLink_olderCoversWithoutOriginal_fallBackToTheResizedCopy() {
            release.setArtworkKey("display.jpg");
            givenReleaseExists();
            when(storageProperties.downloadUrlTtl()).thenReturn(Duration.ofMinutes(5));
            when(storage.presignDownload("display.jpg", "Night Drive - cover.jpg", Duration.ofMinutes(5)))
                .thenReturn("https://storage.test/cover.jpg");

            assertThat(releaseService.artworkDownloadLink(USER_ID, RELEASE_ID).url(), is("https://storage.test/cover.jpg"));
        }

        @Test
        void artworkDownloadLink_withoutUploadedCover_isNotFound() {
            // An external link (artworkUrl) is not in our storage, so there is nothing to sign
            release.setArtworkUrl("https://img.test/external.jpg");
            givenReleaseExists();

            assertThrows(ArtworkNotFoundException.class, () -> releaseService.artworkDownloadLink(USER_ID, RELEASE_ID));
        }

        @Test
        void artworkDownloadLink_otherUser_isRejected() {
            givenReleaseExists();

            assertThrows(ReleaseOwnershipException.class, () -> releaseService.artworkDownloadLink(OTHER_USER_ID, RELEASE_ID));
            verify(storage, never()).presignDownload(anyString(), anyString(), any());
        }
    }

}