package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.SongService;
import dev.danyil.enums.SongStatus;
import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;
import dev.danyil.songs.exceptions.InvalidSongOrderException;
import dev.danyil.songs.exceptions.SongNotFoundException;
import dev.danyil.users.UserEntity;

/**
 * Web layer contract of SongController: nested routes, validation and status codes.
 * Standalone MockMvc: no Spring context and no security filter chain.
 */
class SongControllerTest {

    private static final long USER_ID = 1000L;
    private static final long RELEASE_ID = 5100L;
    private static final long SONG_ID = 7100L;
    private static final String SONGS_URL = "/api/v1/releases/" + RELEASE_ID + "/songs";

    private static final String VALID_SONG = """
        {
          "title": "Night Drive",
          "durationSeconds": 215,
          "isrc": "us-abc-26-00001",
          "status": "RECORDING",
          "lyrics": "Neon on the windshield",
          "explicit": true
        }
        """;

    private MockMvc mockMvc;
    private SongService songService;
    private SongResponseDTO songResponse;

    @BeforeEach
    void setUp() {
        songService = mock(SongService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new SongController(songService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .addPlaceholderValue("api-endpoint", "api/v1")
            .build();

        UserEntity userEntity = new UserEntity();
        userEntity.setId(USER_ID);
        userEntity.setUsername("test1");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(new CustomUserDetails(userEntity), null, List.of()));

        songResponse = SongResponseDTO.builder()
            .id(SONG_ID)
            .trackNumber(1)
            .title("Night Drive")
            .durationSeconds(215)
            .isrc("USABC2600001")
            .status(SongStatus.RECORDING)
            .explicit(true)
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- GET ----------

    @Nested
    @DisplayName("GET /releases/{id}/songs")
    class GetAll {

        @Test
        void getAll_returns200WithTheTracklist() throws Exception {
            when(songService.getAll(USER_ID, RELEASE_ID)).thenReturn(List.of(songResponse));

            mockMvc.perform(get(SONGS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackNumber", is(1)))
                .andExpect(jsonPath("$[0].isrc", is("USABC2600001")))
                .andExpect(jsonPath("$[0].explicit", is(true)));
        }

        @Test
        void getAll_releaseOfAnotherUser_returns403() throws Exception {
            when(songService.getAll(USER_ID, RELEASE_ID)).thenThrow(new ReleaseOwnershipException());

            mockMvc.perform(get(SONGS_URL)).andExpect(status().isForbidden());
        }

        @Test
        void getById_missingSong_returns404() throws Exception {
            when(songService.getById(USER_ID, RELEASE_ID, SONG_ID)).thenThrow(new SongNotFoundException(SONG_ID));

            mockMvc.perform(get(SONGS_URL + "/{songId}", SONG_ID)).andExpect(status().isNotFound());
        }
    }

    // ---------- POST ----------

    @Nested
    @DisplayName("POST /releases/{id}/songs")
    class Create {

        @Test
        void create_validRequest_returns201WithLocation() throws Exception {
            when(songService.create(eq(USER_ID), eq(RELEASE_ID), any(SongRequestDTO.class))).thenReturn(songResponse);

            mockMvc.perform(post(SONGS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_SONG))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/songs/" + SONG_ID)))
                .andExpect(jsonPath("$.trackNumber", is(1)));

            ArgumentCaptor<SongRequestDTO> captor = ArgumentCaptor.forClass(SongRequestDTO.class);
            verify(songService).create(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            // Normalization happens in the mapper; the raw value reaches the service unchanged
            assertThat(captor.getValue().isrc(), is("us-abc-26-00001"));
            assertThat(captor.getValue().explicit(), is(true));
        }

        @Test
        void create_withoutExplicit_isAccepted() throws Exception {
            when(songService.create(eq(USER_ID), eq(RELEASE_ID), any(SongRequestDTO.class))).thenReturn(songResponse);

            mockMvc.perform(post(SONGS_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"title\": \"Intro\", \"status\": \"WRITING\" }"))
                .andExpect(status().isCreated());

            // The mapper turns a missing flag into false; the web layer only has to accept it
            ArgumentCaptor<SongRequestDTO> captor = ArgumentCaptor.forClass(SongRequestDTO.class);
            verify(songService).create(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().explicit(), is(nullValue()));
        }

        @Test
        void create_emptyIsrc_isAllowed() throws Exception {
            when(songService.create(eq(USER_ID), eq(RELEASE_ID), any(SongRequestDTO.class))).thenReturn(songResponse);

            mockMvc.perform(post(SONGS_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"title\": \"Intro\", \"status\": \"WRITING\", \"isrc\": \"\" }"))
                .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            // blank title
            """
            { "title": "   ", "status": "WRITING" }
            """,
            // missing status
            """
            { "title": "Intro" }
            """,
            // unknown status
            """
            { "title": "Intro", "status": "MASTERED" }
            """,
            // zero duration
            """
            { "title": "Intro", "status": "WRITING", "durationSeconds": 0 }
            """,
            // longer than three hours
            """
            { "title": "Intro", "status": "WRITING", "durationSeconds": 10801 }
            """,
            // malformed ISRC
            """
            { "title": "Intro", "status": "WRITING", "isrc": "NOT-AN-ISRC" }
            """
        })
        void create_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(post(SONGS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(songService, never()).create(anyLong(), anyLong(), any());
        }
    }

    // ---------- PUT / PATCH / DELETE ----------

    @Nested
    @DisplayName("PUT, PATCH and DELETE /releases/{id}/songs/{songId}")
    class Modify {

        @Test
        void replace_validRequest_returns200() throws Exception {
            when(songService.replace(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any(SongRequestDTO.class)))
                .thenReturn(songResponse);

            mockMvc.perform(put(SONGS_URL + "/{songId}", SONG_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_SONG))
                .andExpect(status().isOk());
        }

        @Test
        void replace_invalidRequest_returns400() throws Exception {
            mockMvc.perform(put(SONGS_URL + "/{songId}", SONG_ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"title\": \"\" }"))
                .andExpect(status().isBadRequest());

            verify(songService, never()).replace(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        void updateStatus_validRequest_returns200AndPassesTheStatus() throws Exception {
            when(songService.updateStatus(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any(SongStatusUpdateRequestDTO.class)))
                .thenReturn(songResponse);

            mockMvc.perform(patch(SONGS_URL + "/{songId}/status", SONG_ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"status\": \"MIXING_MASTERING\" }"))
                .andExpect(status().isOk());

            ArgumentCaptor<SongStatusUpdateRequestDTO> captor = ArgumentCaptor.forClass(SongStatusUpdateRequestDTO.class);
            verify(songService).updateStatus(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), captor.capture());
            assertThat(captor.getValue().status(), is(SongStatus.MIXING_MASTERING));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = { "{}", "{ \"status\": null }", "{ \"status\": \"FINISHED\" }" })
        void updateStatus_missingOrUnknownStatus_returns400(String body) throws Exception {
            mockMvc.perform(patch(SONGS_URL + "/{songId}/status", SONG_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(songService, never()).updateStatus(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        void delete_returns204() throws Exception {
            mockMvc.perform(delete(SONGS_URL + "/{songId}", SONG_ID)).andExpect(status().isNoContent());

            verify(songService).delete(USER_ID, RELEASE_ID, SONG_ID);
        }

        @Test
        void delete_missingSong_returns404() throws Exception {
            doThrow(new SongNotFoundException(SONG_ID)).when(songService).delete(USER_ID, RELEASE_ID, SONG_ID);

            mockMvc.perform(delete(SONGS_URL + "/{songId}", SONG_ID)).andExpect(status().isNotFound());
        }
    }

    // ---------- PUT /order ----------

    @Nested
    @DisplayName("PUT /releases/{id}/songs/order")
    class Reorder {

        @Test
        void reorder_returns200AndPassesTheIdsInOrder() throws Exception {
            when(songService.reorder(eq(USER_ID), eq(RELEASE_ID), any(SongOrderRequestDTO.class)))
                .thenReturn(List.of(songResponse));

            mockMvc.perform(put(SONGS_URL + "/order").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"songIds\": [7300, 7100, 7200] }"))
                .andExpect(status().isOk());

            ArgumentCaptor<SongOrderRequestDTO> captor = ArgumentCaptor.forClass(SongOrderRequestDTO.class);
            verify(songService).reorder(eq(USER_ID), eq(RELEASE_ID), captor.capture());
            assertThat(captor.getValue().songIds(), contains(7300L, 7100L, 7200L));
        }

        @Test
        void reorder_isNotMistakenForASongId() throws Exception {
            // "order" is a literal path segment: it must never reach the {songId} handler
            when(songService.reorder(eq(USER_ID), eq(RELEASE_ID), any(SongOrderRequestDTO.class))).thenReturn(List.of());

            mockMvc.perform(put(SONGS_URL + "/order").contentType(MediaType.APPLICATION_JSON).content("{ \"songIds\": [] }"))
                .andExpect(status().isOk());

            verify(songService, never()).replace(anyLong(), anyLong(), anyLong(), any());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = { "{}", "{ \"songIds\": null }", "{ \"songIds\": [7100, null] }" })
        void reorder_missingIds_returns400(String body) throws Exception {
            mockMvc.perform(put(SONGS_URL + "/order").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(songService, never()).reorder(anyLong(), anyLong(), any());
        }

        @Test
        void reorder_staleOrder_returns400() throws Exception {
            when(songService.reorder(eq(USER_ID), eq(RELEASE_ID), any(SongOrderRequestDTO.class)))
                .thenThrow(new InvalidSongOrderException());

            mockMvc.perform(put(SONGS_URL + "/order").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"songIds\": [7100] }"))
                .andExpect(status().isBadRequest());
        }
    }
}