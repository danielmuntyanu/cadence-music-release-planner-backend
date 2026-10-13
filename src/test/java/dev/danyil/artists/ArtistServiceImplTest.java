package dev.danyil.artists;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialRequestDTO;
import dev.danyil.artists.exceptions.ArtistAlreadyExistsException;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.artists.exceptions.ArtistOwnershipException;
import dev.danyil.artists.exceptions.DuplicateSocialPlatformException;
import dev.danyil.enums.SocialPlatform;
import dev.danyil.mappers.ArtistMapper;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class ArtistServiceImplTest {

    // Ids are deliberately outside the Long cache range (-128..127).
    // Each autoboxing of these primitives creates a new Long instance,
    // so an accidental reference comparison (==, !=) between ids fails these tests.
    private static final long USER_ID = 1000L;
    private static final long OTHER_USER_ID = 2000L;
    private static final long ARTIST_ID = 4200L;

    private static final String SPOTIFY_URL = "https://open.spotify.com/artist/nova";
    private static final String INSTAGRAM_URL = "https://instagram.com/nova";

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private UserRepository userRepository;

    // The mapper has no dependencies, so the real implementation is used:
    // service tests then verify the actual result, not stubbed mapping.
    @Spy
    private ArtistMapper artistMapper = new ArtistMapper();

    @InjectMocks
    private ArtistServiceImpl artistService;

    private UserEntity owner;
    private ArtistEntity artist;

    @BeforeEach
    void setUp() {
        owner = new UserEntity();
        owner.setId(USER_ID);

        artist = new ArtistEntity();
        artist.setId(ARTIST_ID);
        artist.setName("Nova");
        artist.setDescription("Retro sounds");
        artist.setGenre("Synthwave");
        artist.setUser(owner);
        artist.getSocials().add(new Social(SocialPlatform.SPOTIFY, SPOTIFY_URL));
    }

    // ---------- Test data helpers ----------

    private static ArtistCreateRequestDTO createRequest(String name, List<SocialRequestDTO> socials) {
        return new ArtistCreateRequestDTO(name, null, null, null, null, socials);
    }

    private static ArtistUpdateRequestDTO updateRequest(
            String name, String description, String genre, List<SocialRequestDTO> socials) {
        return new ArtistUpdateRequestDTO(name, null, null, description, genre, socials);
    }

    private static List<SocialRequestDTO> duplicateSpotifyLinks() {
        return List.of(
            new SocialRequestDTO(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/a"),
            new SocialRequestDTO(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/b"));
    }

    private void givenArtistExists() {
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.of(artist));
    }

    private void givenArtistDoesNotExist() {
        when(artistRepository.findById(ARTIST_ID)).thenReturn(Optional.empty());
    }

    // ---------- create ----------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void create_validRequest_savesTrimmedArtistOwnedByCurrentUser() {
            ArtistCreateRequestDTO request = new ArtistCreateRequestDTO(
                "  Nova  ", null, "   ", null, "Synthwave",
                List.of(new SocialRequestDTO(SocialPlatform.SPOTIFY, SPOTIFY_URL)));

            when(artistRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nova")).thenReturn(false);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
            when(artistRepository.save(any(ArtistEntity.class))).thenAnswer(invocation -> {
                ArtistEntity toSave = invocation.getArgument(0);
                toSave.setId(ARTIST_ID);
                return toSave;
            });

            ArtistResponseDTO response = artistService.create(request, USER_ID);

            ArgumentCaptor<ArtistEntity> captor = ArgumentCaptor.forClass(ArtistEntity.class);
            verify(artistRepository).save(captor.capture());
            ArtistEntity saved = captor.getValue();

            assertThat(saved.getName(), is("Nova"));
            assertThat(saved.getBannerUrl(), is(nullValue()));
            assertThat(saved.getGenre(), is("Synthwave"));
            assertThat(saved.getUser(), is(sameInstance(owner)));
            assertThat(saved.getSocials(), contains(new Social(SocialPlatform.SPOTIFY, SPOTIFY_URL)));

            assertThat(response.id(), is(ARTIST_ID));
            assertThat(response.name(), is("Nova"));
            assertThat(response.socials(), hasSize(1));
        }

        @Test
        void create_nameAlreadyUsedByUser_throwsAndDoesNotSave() {
            when(artistRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nova")).thenReturn(true);

            assertThrows(ArtistAlreadyExistsException.class,
                () -> artistService.create(createRequest("Nova", null), USER_ID));

            verify(artistRepository, never()).save(any());
        }

        @Test
        void create_duplicateSocialPlatforms_throwsAndDoesNotSave() {
            when(artistRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Nova")).thenReturn(false);

            assertThrows(DuplicateSocialPlatformException.class,
                () -> artistService.create(createRequest("Nova", duplicateSpotifyLinks()), USER_ID));

            verify(artistRepository, never()).save(any());
        }
    }

    // ---------- getAll ----------

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        void getAll_returnsMappedPageOfCurrentUserArtists() {
            Pageable pageable = PageRequest.of(0, 10);
            when(artistRepository.findAllByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(artist), pageable, 1));

            Page<ArtistResponseDTO> page = artistService.getAll(USER_ID, pageable);

            assertThat(page.getTotalElements(), is(1L));
            assertThat(page.getContent(), hasSize(1));
            assertThat(page.getContent().get(0).name(), is("Nova"));
        }

        @Test
        void getAll_userWithoutArtists_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(artistRepository.findAllByUserId(USER_ID, pageable)).thenReturn(Page.empty(pageable));

            Page<ArtistResponseDTO> page = artistService.getAll(USER_ID, pageable);

            assertThat(page.getContent(), is(empty()));
        }
    }

    // ---------- getById ----------

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void getById_ownerRequests_returnsArtist() {
            givenArtistExists();

            ArtistResponseDTO response = artistService.getById(USER_ID, ARTIST_ID);

            assertThat(response.id(), is(ARTIST_ID));
            assertThat(response.name(), is("Nova"));
            assertThat(response.socials(), hasSize(1));
        }

        @Test
        void getById_artistDoesNotExist_throwsNotFound() {
            givenArtistDoesNotExist();

            assertThrows(ArtistNotFoundException.class,
                () -> artistService.getById(USER_ID, ARTIST_ID));
        }

        @Test
        void getById_otherUserRequests_throwsOwnership() {
            givenArtistExists();

            assertThrows(ArtistOwnershipException.class,
                () -> artistService.getById(OTHER_USER_ID, ARTIST_ID));
        }
    }

    // ---------- update ----------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void update_onlyGenreProvided_changesGenreAndKeepsOtherFields() {
            givenArtistExists();

            ArtistResponseDTO response = artistService.update(
                USER_ID, ARTIST_ID, updateRequest(null, null, "Darkwave", null));

            assertThat(response.genre(), is("Darkwave"));
            assertThat(artist.getName(), is("Nova"));
            assertThat(artist.getDescription(), is("Retro sounds"));
            assertThat(artist.getSocials(), hasSize(1));
            verify(artistRepository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), anyString());
        }

        @Test
        void update_emptyDescription_clearsField() {
            givenArtistExists();

            artistService.update(USER_ID, ARTIST_ID, updateRequest(null, "", null, null));

            assertThat(artist.getDescription(), is(nullValue()));
        }

        @Test
        void update_socialsProvided_replacesWholeCollection() {
            givenArtistExists();
            List<SocialRequestDTO> socials =
                List.of(new SocialRequestDTO(SocialPlatform.INSTAGRAM, INSTAGRAM_URL));

            artistService.update(USER_ID, ARTIST_ID, updateRequest(null, null, null, socials));

            assertThat(artist.getSocials(), contains(new Social(SocialPlatform.INSTAGRAM, INSTAGRAM_URL)));
        }

        @Test
        void update_emptySocials_removesAllLinks() {
            givenArtistExists();

            artistService.update(USER_ID, ARTIST_ID, updateRequest(null, null, null, List.of()));

            assertThat(artist.getSocials(), is(empty()));
        }

        @Test
        void update_newNameTakenByUser_throwsAndKeepsOldName() {
            givenArtistExists();
            when(artistRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Echo")).thenReturn(true);

            assertThrows(ArtistAlreadyExistsException.class,
                () -> artistService.update(USER_ID, ARTIST_ID, updateRequest("Echo", null, null, null)));

            assertThat(artist.getName(), is("Nova"));
        }

        @Test
        void update_sameNameDifferentCase_skipsUniquenessCheck() {
            givenArtistExists();

            artistService.update(USER_ID, ARTIST_ID, updateRequest("NOVA", null, null, null));

            assertThat(artist.getName(), is("NOVA"));
            verify(artistRepository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), anyString());
        }

        @Test
        void update_duplicateSocialPlatforms_throwsAndKeepsOldLinks() {
            givenArtistExists();

            assertThrows(DuplicateSocialPlatformException.class,
                () -> artistService.update(
                    USER_ID, ARTIST_ID, updateRequest(null, null, null, duplicateSpotifyLinks())));

            assertThat(artist.getSocials(), contains(new Social(SocialPlatform.SPOTIFY, SPOTIFY_URL)));
        }

        @Test
        void update_otherUserRequests_throwsAndLeavesArtistUnchanged() {
            givenArtistExists();

            assertThrows(ArtistOwnershipException.class,
                () -> artistService.update(
                    OTHER_USER_ID, ARTIST_ID, updateRequest("Hacked", null, null, null)));

            assertThat(artist.getName(), is("Nova"));
            verify(artistMapper, never()).applyUpdate(any(), any());
        }

        @Test
        void update_artistDoesNotExist_throwsNotFound() {
            givenArtistDoesNotExist();

            assertThrows(ArtistNotFoundException.class,
                () -> artistService.update(USER_ID, ARTIST_ID, updateRequest(null, null, "Darkwave", null)));
        }
    }

    // ---------- delete ----------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void delete_ownerRequests_deletesArtist() {
            givenArtistExists();

            artistService.delete(USER_ID, ARTIST_ID);

            verify(artistRepository).delete(artist);
        }

        @Test
        void delete_otherUserRequests_throwsAndDoesNotDelete() {
            givenArtistExists();

            assertThrows(ArtistOwnershipException.class,
                () -> artistService.delete(OTHER_USER_ID, ARTIST_ID));

            verify(artistRepository, never()).delete(any());
        }

        @Test
        void delete_artistDoesNotExist_throwsAndDoesNotDelete() {
            givenArtistDoesNotExist();

            assertThrows(ArtistNotFoundException.class,
                () -> artistService.delete(USER_ID, ARTIST_ID));

            verify(artistRepository, never()).delete(any());
        }
    }
}
