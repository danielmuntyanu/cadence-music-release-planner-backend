package dev.danyil.releases;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.ReleaseService;
import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.releases.dtos.ReleaseArtistResponseDTO;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseDatesRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleaseGenresRequestDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import dev.danyil.releases.exceptions.ReleaseAlreadyExistsException;
import dev.danyil.releases.exceptions.ReleaseNotFoundException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.users.UserEntity;

/**
 * Web layer contract of ReleaseController: routing, request validation,
 * query-string binding, JSON shape and HTTP status codes.
 *
 * Standalone MockMvc: no Spring context and no security filter chain.
 */
class ReleaseControllerTest {

    private static final long USER_ID = 1000L;
    private static final long RELEASE_ID = 5100L;
    private static final long NOVA_ID = 4200L;
    private static final long ADA_ID = 4300L;
    private static final String RELEASES_URL = "/api/v1/releases";

    private MockMvc mockMvc;
    private ReleaseService releaseService;
    private ReleaseResponseDTO releaseResponse;

    @BeforeEach
    void setUp() {
        releaseService = mock(ReleaseService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new ReleaseController(releaseService))
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

        releaseResponse = ReleaseResponseDTO.builder()
            .id(RELEASE_ID)
            .name("Night Drive")
            .type(ReleaseType.EP)
            .phase(ReleasePhase.PLANNING)
            .primaryGenre(Genre.POP)
            .secondaryGenre(Genre.ELECTRONIC)
            .releaseDate(LocalDate.of(2026, 11, 14))
            .artists(List.of(
                ReleaseArtistResponseDTO.builder().id(ADA_ID).name("Ada").build(),
                ReleaseArtistResponseDTO.builder().id(NOVA_ID).name("Nova").build()))
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- POST /releases ----------

    @Nested
    @DisplayName("POST /releases")
    class Create {

        @Test
        void create_validRequest_returns201WithLocationAndOrderedArtists() throws Exception {
            when(releaseService.create(any(ReleaseCreateRequestDTO.class), eq(USER_ID))).thenReturn(releaseResponse);

            mockMvc.perform(post(RELEASES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "name": "Night Drive",
                          "type": "EP",
                          "primaryGenre": "POP",
                          "secondaryGenre": "ELECTRONIC",
                          "releaseDate": "2026-11-14",
                          "artistIds": [4300, 4200]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/releases/" + RELEASE_ID)))
                .andExpect(jsonPath("$.id", is((int) RELEASE_ID)))
                .andExpect(jsonPath("$.releaseDate", is("2026-11-14")))
                .andExpect(jsonPath("$.artists", hasSize(2)))
                .andExpect(jsonPath("$.artists[0].name", is("Ada")))
                .andExpect(jsonPath("$.user").doesNotExist());

            ArgumentCaptor<ReleaseCreateRequestDTO> captor = ArgumentCaptor.forClass(ReleaseCreateRequestDTO.class);
            verify(releaseService).create(captor.capture(), eq(USER_ID));
            assertThat(captor.getValue().artistIds(), contains(ADA_ID, NOVA_ID));
            assertThat(captor.getValue().releaseDate(), is(LocalDate.of(2026, 11, 14)));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            // blank name
            """
            { "name": "   ", "type": "EP", "primaryGenre": "POP" }
            """,
            // missing primary genre
            """
            { "name": "Night Drive", "type": "EP" }
            """,
            // unknown enum value
            """
            { "name": "Night Drive", "type": "EP", "primaryGenre": "POLKA_STEP" }
            """,
            // same primary and secondary genre (@AssertTrue)
            """
            { "name": "Night Drive", "type": "EP", "primaryGenre": "POP", "secondaryGenre": "POP" }
            """,
            // duplicate artist (@AssertTrue)
            """
            { "name": "Night Drive", "type": "EP", "primaryGenre": "POP", "artistIds": [4200, 4200] }
            """,
            // original release date after the release date (@AssertTrue)
            """
            { "name": "Night Drive", "type": "EP", "primaryGenre": "POP",
              "releaseDate": "2026-01-01", "originalReleaseDate": "2026-06-01" }
            """
        })
        void create_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(post(RELEASES_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).create(any(), anyLong());
        }

        @Test
        void create_nameAlreadyExists_returns409() throws Exception {
            when(releaseService.create(any(ReleaseCreateRequestDTO.class), eq(USER_ID)))
                .thenThrow(new ReleaseAlreadyExistsException("Night Drive"));

            mockMvc.perform(post(RELEASES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Night Drive", "type": "EP", "primaryGenre": "POP" }
                        """))
                .andExpect(status().isConflict());
        }

        @Test
        void create_unknownArtist_returns404() throws Exception {
            when(releaseService.create(any(ReleaseCreateRequestDTO.class), eq(USER_ID)))
                .thenThrow(new ArtistNotFoundException(9900L));

            mockMvc.perform(post(RELEASES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Night Drive", "type": "EP", "primaryGenre": "POP", "artistIds": [9900] }
                        """))
                .andExpect(status().isNotFound());
        }

        @Test
        void create_withoutType_isAcceptedAndPassesNullToService() throws Exception {
            when(releaseService.create(any(ReleaseCreateRequestDTO.class), eq(USER_ID))).thenReturn(releaseResponse);

            mockMvc.perform(post(RELEASES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Night Drive", "primaryGenre": "POP" }
                        """))
                .andExpect(status().isCreated());

            ArgumentCaptor<ReleaseCreateRequestDTO> captor = ArgumentCaptor.forClass(ReleaseCreateRequestDTO.class);
            verify(releaseService).create(captor.capture(), eq(USER_ID));
            assertThat(captor.getValue().type(), is(nullValue()));
        }

        @Test
        void create_ignoresArtworkUrl_theServerOwnsTheCover() throws Exception {
            when(releaseService.create(any(ReleaseCreateRequestDTO.class), eq(USER_ID))).thenReturn(releaseResponse);

            mockMvc.perform(post(RELEASES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Night Drive", "primaryGenre": "POP", "artworkUrl": "https://evil.test/cover.jpg" }
                        """))
                .andExpect(status().isCreated());

            // The DTO has no artworkUrl field: there is no way for the value to reach the service
            verify(releaseService).create(any(ReleaseCreateRequestDTO.class), eq(USER_ID));
        }
    }

    // ---------- GET /releases ----------

    @Nested
    @DisplayName("GET /releases")
    class GetAll {

        private void givenServiceReturnsOneRelease() {
            when(releaseService.getAll(eq(USER_ID), any(ReleaseFilterDTO.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(releaseResponse), PageRequest.of(0, 24), 1));
        }

        @Test
        void getAll_returns200WithPageContent() throws Exception {
            givenServiceReturnsOneRelease();

            mockMvc.perform(get(RELEASES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Night Drive")))
                .andExpect(jsonPath("$.totalElements", is(1)));
        }

        @Test
        void getAll_withoutParams_usesNewestFirstDefaultsAndEmptyFilter() throws Exception {
            givenServiceReturnsOneRelease();

            mockMvc.perform(get(RELEASES_URL)).andExpect(status().isOk());

            ArgumentCaptor<ReleaseFilterDTO> filter = ArgumentCaptor.forClass(ReleaseFilterDTO.class);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(releaseService).getAll(eq(USER_ID), filter.capture(), pageable.capture());

            assertThat(filter.getValue().phase(), is(nullValue()));
            assertThat(filter.getValue().q(), is(nullValue()));
            assertThat(pageable.getValue().getPageSize(), is(24));
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection(), is(Sort.Direction.DESC));
        }

        @Test
        void getAll_bindsRepeatedAndSingleFilterParams() throws Exception {
            givenServiceReturnsOneRelease();

            mockMvc.perform(get(RELEASES_URL)
                    .param("phase", "PRE_RELEASE", "PROMO")
                    .param("type", "EP")
                    .param("genre", "POP")
                    .param("artistId", String.valueOf(NOVA_ID))
                    .param("q", "night")
                    .param("sort", "releaseDate,asc")
                    .param("page", "1")
                    .param("size", "5"))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleaseFilterDTO> filter = ArgumentCaptor.forClass(ReleaseFilterDTO.class);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(releaseService).getAll(eq(USER_ID), filter.capture(), pageable.capture());

            assertThat(filter.getValue().phase(), containsInAnyOrder(ReleasePhase.PRE_RELEASE, ReleasePhase.PROMO));
            assertThat(filter.getValue().type(), contains(ReleaseType.EP));
            assertThat(filter.getValue().genre(), contains(Genre.POP));
            assertThat(filter.getValue().artistId(), is(NOVA_ID));
            assertThat(filter.getValue().q(), is("night"));
            assertThat(pageable.getValue().getPageNumber(), is(1));
            assertThat(pageable.getValue().getPageSize(), is(5));
            assertThat(pageable.getValue().getSort().getOrderFor("releaseDate").getDirection(), is(Sort.Direction.ASC));
        }

        @Test
        void getAll_unknownFilterValue_returns400AndDoesNotCallService() throws Exception {
            mockMvc.perform(get(RELEASES_URL).param("phase", "FOO"))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).getAll(anyLong(), any(), any());
        }
    }

    // ---------- GET /releases/{id} ----------

    @Nested
    @DisplayName("GET /releases/{id}")
    class GetById {

        @Test
        void getById_existingRelease_returns200() throws Exception {
            when(releaseService.getById(USER_ID, RELEASE_ID)).thenReturn(releaseResponse);

            mockMvc.perform(get(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) RELEASE_ID)))
                .andExpect(jsonPath("$.type", is("EP")))
                .andExpect(jsonPath("$.notes", nullValue()));
        }

        @Test
        void getById_releaseNotFound_returns404() throws Exception {
            when(releaseService.getById(USER_ID, RELEASE_ID)).thenThrow(new ReleaseNotFoundException(RELEASE_ID));

            mockMvc.perform(get(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void getById_releaseOfAnotherUser_returns403() throws Exception {
            when(releaseService.getById(USER_ID, RELEASE_ID)).thenThrow(new ReleaseOwnershipException());

            mockMvc.perform(get(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isForbidden());
        }
    }

    // ---------- PATCH /releases/{id} ----------

    @Nested
    @DisplayName("PATCH /releases/{id}")
    class Update {

        @Test
        void update_partialRequest_returns200AndKeepsMissingFieldsNull() throws Exception {
            when(releaseService.update(eq(USER_ID), eq(RELEASE_ID), any(ReleaseUpdateRequestDTO.class)))
                .thenReturn(releaseResponse);

            mockMvc.perform(patch(RELEASES_URL + "/{id}", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "notes": "Final master approved" }
                        """))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleaseUpdateRequestDTO> captor = ArgumentCaptor.forClass(ReleaseUpdateRequestDTO.class);
            verify(releaseService).update(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().notes(), is("Final master approved"));
            assertThat(captor.getValue().name(), is(nullValue()));
            assertThat(captor.getValue().artistIds(), is(nullValue()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            """
            { "name": "   " }
            """,
            """
            { "artistIds": [4200, 4200] }
            """
        })
        void update_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(patch(RELEASES_URL + "/{id}", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).update(anyLong(), anyLong(), any());
        }

        @Test
        void update_nameTakenByAnotherRelease_returns409() throws Exception {
            when(releaseService.update(eq(USER_ID), eq(RELEASE_ID), any(ReleaseUpdateRequestDTO.class)))
                .thenThrow(new ReleaseAlreadyExistsException("Echoes"));

            mockMvc.perform(patch(RELEASES_URL + "/{id}", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "name": "Echoes" }
                        """))
                .andExpect(status().isConflict());
        }
    }

    // ---------- PATCH /releases/{id}/phase ----------

    @Nested
    @DisplayName("PATCH /releases/{id}/phase")
    class UpdatePhase {

        @Test
        void updatePhase_validRequest_returns200AndPassesPhase() throws Exception {
            when(releaseService.updatePhase(eq(USER_ID), eq(RELEASE_ID), any(ReleasePhaseUpdateRequestDTO.class)))
                .thenReturn(releaseResponse);

            mockMvc.perform(patch(RELEASES_URL + "/{id}/phase", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "phase": "PROMO" }
                        """))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleasePhaseUpdateRequestDTO> captor = ArgumentCaptor.forClass(ReleasePhaseUpdateRequestDTO.class);
            verify(releaseService).updatePhase(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().phase(), is(ReleasePhase.PROMO));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = { "{}", "{ \"phase\": null }", "{ \"phase\": \"LAUNCHED\" }" })
        void updatePhase_missingOrUnknownPhase_returns400(String body) throws Exception {
            mockMvc.perform(patch(RELEASES_URL + "/{id}/phase", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).updatePhase(anyLong(), anyLong(), any());
        }
    }

    // ---------- DELETE /releases/{id} ----------

    @Nested
    @DisplayName("DELETE /releases/{id}")
    class Delete {

        @Test
        void delete_existingRelease_returns204() throws Exception {
            mockMvc.perform(delete(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isNoContent());

            verify(releaseService).delete(USER_ID, RELEASE_ID);
        }

        @Test
        void delete_releaseNotFound_returns404() throws Exception {
            doThrow(new ReleaseNotFoundException(RELEASE_ID)).when(releaseService).delete(USER_ID, RELEASE_ID);

            mockMvc.perform(delete(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        void delete_releaseOfAnotherUser_returns403() throws Exception {
            doThrow(new ReleaseOwnershipException()).when(releaseService).delete(USER_ID, RELEASE_ID);

            mockMvc.perform(delete(RELEASES_URL + "/{id}", RELEASE_ID))
                .andExpect(status().isForbidden());
        }
    }

        // ---------- PUT /releases/{id}/genres ----------

    @Nested
    @DisplayName("PUT /releases/{id}/genres")
    class UpdateGenres {

        @Test
        void updateGenres_validRequest_returns200AndPassesBothGenres() throws Exception {
            when(releaseService.updateGenres(eq(USER_ID), eq(RELEASE_ID), any(ReleaseGenresRequestDTO.class)))
                .thenReturn(releaseResponse);

            mockMvc.perform(put(RELEASES_URL + "/{id}/genres", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "primaryGenre": "ROCK", "secondaryGenre": null }
                        """))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleaseGenresRequestDTO> captor = ArgumentCaptor.forClass(ReleaseGenresRequestDTO.class);
            verify(releaseService).updateGenres(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().primaryGenre(), is(Genre.ROCK));
            assertThat(captor.getValue().secondaryGenre(), is(nullValue()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            // primary genre is required
            """
            { "secondaryGenre": "POP" }
            """,
            // the same genre twice
            """
            { "primaryGenre": "POP", "secondaryGenre": "POP" }
            """,
            // unknown enum value
            """
            { "primaryGenre": "POLKA_STEP" }
            """
        })
        void updateGenres_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(put(RELEASES_URL + "/{id}/genres", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).updateGenres(anyLong(), anyLong(), any());
        }
    }

    // ---------- PUT /releases/{id}/dates ----------

    @Nested
    @DisplayName("PUT /releases/{id}/dates")
    class UpdateDates {

        @Test
        void updateDates_validRequest_returns200AndPassesBothDates() throws Exception {
            when(releaseService.updateDates(eq(USER_ID), eq(RELEASE_ID), any(ReleaseDatesRequestDTO.class)))
                .thenReturn(releaseResponse);

            mockMvc.perform(put(RELEASES_URL + "/{id}/dates", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        { "releaseDate": "2026-12-04", "originalReleaseDate": "2020-05-01" }
                        """))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleaseDatesRequestDTO> captor = ArgumentCaptor.forClass(ReleaseDatesRequestDTO.class);
            verify(releaseService).updateDates(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().releaseDate(), is(LocalDate.of(2026, 12, 4)));
            assertThat(captor.getValue().originalReleaseDate(), is(LocalDate.of(2020, 5, 1)));
        }

        @Test
        void updateDates_emptyBody_isValidAndClearsBothDates() throws Exception {
            when(releaseService.updateDates(eq(USER_ID), eq(RELEASE_ID), any(ReleaseDatesRequestDTO.class)))
                .thenReturn(releaseResponse);

            mockMvc.perform(put(RELEASES_URL + "/{id}/dates", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isOk());

            ArgumentCaptor<ReleaseDatesRequestDTO> captor = ArgumentCaptor.forClass(ReleaseDatesRequestDTO.class);
            verify(releaseService).updateDates(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().releaseDate(), is(nullValue()));
            assertThat(captor.getValue().originalReleaseDate(), is(nullValue()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            // original release after the release date
            """
            { "releaseDate": "2026-01-01", "originalReleaseDate": "2026-06-01" }
            """,
            // the same day is not "before"
            """
            { "releaseDate": "2026-01-01", "originalReleaseDate": "2026-01-01" }
            """,
            // not a date
            """
            { "releaseDate": "next friday" }
            """
        })
        void updateDates_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(put(RELEASES_URL + "/{id}/dates", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());

            verify(releaseService, never()).updateDates(anyLong(), anyLong(), any());
        }

        @Test
        void updateDates_missingBody_returns400() throws Exception {
            mockMvc.perform(put(RELEASES_URL + "/{id}/dates", RELEASE_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }
}