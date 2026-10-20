package dev.danyil.artists;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialResponseDTO;
import dev.danyil.artists.exceptions.ArtistAlreadyExistsException;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.artists.exceptions.ArtistOwnershipException;
import dev.danyil.artists.exceptions.DuplicateSocialPlatformException;
import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.ArtistService;
import dev.danyil.enums.SocialPlatform;
import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.users.UserEntity;

/**
 * Web layer contract of ArtistController: routing, request validation,
 * JSON shape and HTTP status codes.
 *
 * Standalone MockMvc: no Spring context and no security filter chain.
 * The current user is placed in the SecurityContext directly, and
 * AuthenticationPrincipalArgumentResolver injects it into the controller.
 */
class ArtistControllerTest {

    private static final long USER_ID = 1000L;
    private static final long ARTIST_ID = 4200L;
    private static final String ARTISTS_URL = "/api/v1/artists";
    private static final String SPOTIFY_URL = "https://open.spotify.com/artist/nova";

    private MockMvc mockMvc;
    private ArtistService artistService;
    private ArtistResponseDTO artistResponse;

    @BeforeEach
    void setUp() {
        artistService = mock(ArtistService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ArtistController(artistService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new PageableHandlerMethodArgumentResolver(),
                new AuthenticationPrincipalArgumentResolver())
            .addPlaceholderValue("api-endpoint", "api/v1")
            .build();

        UserEntity userEntity = new UserEntity();
        userEntity.setId(USER_ID);
        userEntity.setUsername("test1");
        
        CustomUserDetails currentUser = new CustomUserDetails(userEntity);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(currentUser, null, List.of()));

        artistResponse = new ArtistResponseDTO(
            ARTIST_ID, "Nova", null, null, "Retro sounds", "Synthwave",
            List.of(new SocialResponseDTO(SocialPlatform.SPOTIFY, SPOTIFY_URL)), 0L);
    }

    @AfterEach
    void clearSecurityContext() {
        // The context is thread-bound: without clearing, the user leaks into other test classes.
        SecurityContextHolder.clearContext();
    }

    // ---------- POST /artists ----------

    @Nested
    @DisplayName("POST /artists")
    class Create {

        @Test
        void create_validRequest_returns201WithLocationAndBody() throws Exception {
            when(artistService.create(any(ArtistCreateRequestDTO.class), eq(USER_ID)))
                .thenReturn(artistResponse);

            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Nova",
                          "genre": "Synthwave",
                          "socials": [
                            { "platform": "SPOTIFY", "url": "https://open.spotify.com/artist/nova" }
                          ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/artists/" + ARTIST_ID)))
                .andExpect(jsonPath("$.id", is((int) ARTIST_ID)))
                .andExpect(jsonPath("$.name", is("Nova")))
                .andExpect(jsonPath("$.socials", hasSize(1)))
                .andExpect(jsonPath("$.socials[0].platform", is("SPOTIFY")))
                .andExpect(jsonPath("$.user").doesNotExist());

            ArgumentCaptor<ArtistCreateRequestDTO> captor = ArgumentCaptor.forClass(ArtistCreateRequestDTO.class);
            verify(artistService).create(captor.capture(), eq(USER_ID));
            assertThat(captor.getValue().name(), is("Nova"));
            assertThat(captor.getValue().socials(), hasSize(1));
        }

        @Test
        void create_blankName_returns400AndDoesNotCallService() throws Exception {
            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "   " }
                        """))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).create(any(), anyLong());
        }

        @Test
        void create_nameTooLong_returns400() throws Exception {
            String longName = "a".repeat(51);

            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"name\": \"" + longName + "\" }"))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).create(any(), anyLong());
        }

        @Test
        void create_invalidAvatarUrl_returns400() throws Exception {
            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Nova", "avatarUrl": "not a url" }
                        """))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).create(any(), anyLong());
        }

        @Test
        void create_invalidNestedSocial_returns400() throws Exception {
            // Proves that @Valid cascades into list elements.
            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Nova",
                          "socials": [ { "platform": null, "url": "not a url" } ]
                        }
                        """))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).create(any(), anyLong());
        }

        @Test
        void create_unknownSocialPlatform_returns400() throws Exception {
            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Nova",
                          "socials": [ { "platform": "MYSPACE", "url": "https://myspace.com/nova" } ]
                        }
                        """))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).create(any(), anyLong());
        }

        @Test
        void create_nameAlreadyExists_returns409() throws Exception {
            when(artistService.create(any(ArtistCreateRequestDTO.class), eq(USER_ID)))
                .thenThrow(new ArtistAlreadyExistsException("Nova"));

            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Nova" }
                        """))
                .andExpect(status().isConflict());
        }

        @Test
        void create_duplicateSocialPlatform_returns400() throws Exception {
            when(artistService.create(any(ArtistCreateRequestDTO.class), eq(USER_ID)))
                .thenThrow(new DuplicateSocialPlatformException(SocialPlatform.SPOTIFY));

            mockMvc.perform(post(ARTISTS_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Nova",
                          "socials": [
                            { "platform": "SPOTIFY", "url": "https://open.spotify.com/artist/a" },
                            { "platform": "SPOTIFY", "url": "https://open.spotify.com/artist/b" }
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest());
        }
    }

    // ---------- GET /artists ----------

    @Nested
    @DisplayName("GET /artists")
    class GetAll {

        @Test
        void getAll_returns200WithPageContent() throws Exception {
            Pageable pageable = PageRequest.of(0, 20);
            when(artistService.getAll(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artistResponse), pageable, 1));

            mockMvc.perform(get(ARTISTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Nova")))
                .andExpect(jsonPath("$.totalElements", is(1)));
        }

        @Test
        void getAll_passesPagingParamsToService() throws Exception {
            when(artistService.getAll(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

            mockMvc.perform(get(ARTISTS_URL).param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(artistService).getAll(eq(USER_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber(), is(1));
            assertThat(captor.getValue().getPageSize(), is(5));
        }
    }

    // ---------- GET /artists/{id} ----------

    @Nested
    @DisplayName("GET /artists/{id}")
    class GetById {

        @Test
        void getById_existingArtist_returns200() throws Exception {
            when(artistService.getById(USER_ID, ARTIST_ID)).thenReturn(artistResponse);

            mockMvc.perform(get(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) ARTIST_ID)))
                .andExpect(jsonPath("$.description", is("Retro sounds")))
                .andExpect(jsonPath("$.avatarUrl", nullValue()));
        }

        @Test
        void getById_artistNotFound_returns404() throws Exception {
            when(artistService.getById(USER_ID, ARTIST_ID)).thenThrow(new ArtistNotFoundException(ARTIST_ID));

            mockMvc.perform(get(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void getById_artistOfAnotherUser_returns403() throws Exception {
            when(artistService.getById(USER_ID, ARTIST_ID)).thenThrow(new ArtistOwnershipException());

            mockMvc.perform(get(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isForbidden());
        }

        @Test
        void getById_nonNumericId_returns400() throws Exception {
            mockMvc.perform(get(ARTISTS_URL + "/abc"))
                .andExpect(status().isBadRequest());
        }
    }

    // ---------- PATCH /artists/{id} ----------

    @Nested
    @DisplayName("PATCH /artists/{id}")
    class Update {

        @Test
        void update_partialRequest_returns200AndKeepsMissingFieldsNull() throws Exception {
            when(artistService.update(eq(USER_ID), eq(ARTIST_ID), any(ArtistUpdateRequestDTO.class)))
                .thenReturn(artistResponse);

            mockMvc.perform(patch(ARTISTS_URL + "/{id}", ARTIST_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "genre": "Synthwave" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre", is("Synthwave")));

            // Missing JSON fields must arrive as null, meaning "leave unchanged".
            ArgumentCaptor<ArtistUpdateRequestDTO> captor = ArgumentCaptor.forClass(ArtistUpdateRequestDTO.class);
            verify(artistService).update(eq(USER_ID), eq(ARTIST_ID), captor.capture());
            assertThat(captor.getValue().genre(), is("Synthwave"));
            assertThat(captor.getValue().name(), is(nullValue()));
            assertThat(captor.getValue().socials(), is(nullValue()));
        }

        @Test
        void update_blankName_returns400AndDoesNotCallService() throws Exception {
            // Fails if @Valid is missing on the update handler.
            mockMvc.perform(patch(ARTISTS_URL + "/{id}", ARTIST_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "   " }
                        """))
                .andExpect(status().isBadRequest());

            verify(artistService, never()).update(anyLong(), anyLong(), any());
        }

        @Test
        void update_nameTakenByAnotherArtist_returns409() throws Exception {
            when(artistService.update(eq(USER_ID), eq(ARTIST_ID), any(ArtistUpdateRequestDTO.class)))
                .thenThrow(new ArtistAlreadyExistsException("Echo"));

            mockMvc.perform(patch(ARTISTS_URL + "/{id}", ARTIST_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Echo" }
                        """))
                .andExpect(status().isConflict());
        }
    }

    // ---------- DELETE /artists/{id} ----------

    @Nested
    @DisplayName("DELETE /artists/{id}")
    class Delete {

        @Test
        void delete_existingArtist_returns204() throws Exception {
            mockMvc.perform(delete(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isNoContent());

            verify(artistService).delete(USER_ID, ARTIST_ID);
        }

        @Test
        void delete_artistNotFound_returns404() throws Exception {
            doThrow(new ArtistNotFoundException(ARTIST_ID)).when(artistService).delete(USER_ID, ARTIST_ID);

            mockMvc.perform(delete(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void delete_artistOfAnotherUser_returns403() throws Exception {
            doThrow(new ArtistOwnershipException()).when(artistService).delete(USER_ID, ARTIST_ID);

            mockMvc.perform(delete(ARTISTS_URL + "/{id}", ARTIST_ID))
                .andExpect(status().isForbidden());
        }
    }
}