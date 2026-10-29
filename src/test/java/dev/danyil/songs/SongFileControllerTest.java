package dev.danyil.songs;

import static org.hamcrest.MatcherAssert.assertThat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import dev.danyil.contracts.SongFileService;
import dev.danyil.global.GlobalExceptionHandler;
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
import dev.danyil.storage.dtos.DownloadLinkDTO;
import dev.danyil.users.UserEntity;

/**
 * Web layer contract of SongFileController: routes, validation and status codes.
 * Standalone MockMvc: no Spring context and no security filter chain.
 */
class SongFileControllerTest {

    private static final long USER_ID = 1000L;
    private static final long RELEASE_ID = 5100L;
    private static final long SONG_ID = 7100L;
    private static final long FILE_ID = 8100L;
    private static final String SONG_URL = "/api/v1/releases/" + RELEASE_ID + "/songs/" + SONG_ID;
    private static final String FILES_URL = SONG_URL + "/files";

    private MockMvc mockMvc;
    private SongFileService service;
    private SongFileResponseDTO fileResponse;

    @BeforeEach
    void setUp() {
        service = mock(SongFileService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new SongFileController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .addPlaceholderValue("api-endpoint", "api/v1")
            .build();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("test1");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));

        fileResponse = SongFileResponseDTO.builder()
            .id(FILE_ID).displayName("mix3").originalFilename("mix3.wav")
            .contentType("audio/wav").sizeBytes(800).primary(true)
            .url("https://signed.test/mix3.wav").build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Reading ----------

    @Test
    void getAll_returns200WithTheFiles() throws Exception {
        when(service.getAll(USER_ID, RELEASE_ID, SONG_ID)).thenReturn(List.of(fileResponse));

        mockMvc.perform(get(FILES_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].primary", is(true)))
            .andExpect(jsonPath("$[0].url", is("https://signed.test/mix3.wav")));
    }

    @Test
    void getAll_songOfAnotherUser_returns403() throws Exception {
        when(service.getAll(USER_ID, RELEASE_ID, SONG_ID)).thenThrow(new ReleaseOwnershipException());

        mockMvc.perform(get(FILES_URL)).andExpect(status().isForbidden());
    }

    @Test
    void downloadLink_returns200WithTheLink() throws Exception {
        when(service.downloadLink(USER_ID, RELEASE_ID, SONG_ID, FILE_ID))
            .thenReturn(new DownloadLinkDTO("https://storage.test/download", Instant.parse("2026-10-27T12:05:00Z")));

        mockMvc.perform(get(FILES_URL + "/{fileId}/download", FILE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url", is("https://storage.test/download")));
    }

    @Test
    void downloadLink_missingFile_returns404() throws Exception {
        when(service.downloadLink(USER_ID, RELEASE_ID, SONG_ID, FILE_ID)).thenThrow(new SongFileNotFoundException(FILE_ID));

        mockMvc.perform(get(FILES_URL + "/{fileId}/download", FILE_ID)).andExpect(status().isNotFound());
    }

    // ---------- Uploads ----------

    @Nested
    @DisplayName("POST /files/uploads and POST /files")
    class Uploads {

        @Test
        void startUpload_returns200WithTheSignedPut() throws Exception {
            when(service.startUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any(SongFileUploadRequestDTO.class)))
                .thenReturn(new SongFileUploadResponseDTO("users/1000/song-files/k.wav", "https://storage.test/put",
                    Map.of("content-type", "audio/wav"), Instant.parse("2026-10-27T12:15:00Z")));

            mockMvc.perform(post(FILES_URL + "/uploads").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"fileName\": \"mix3.wav\", \"sizeBytes\": 800 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl", is("https://storage.test/put")))
                .andExpect(jsonPath("$.headers.content-type", is("audio/wav")));

            ArgumentCaptor<SongFileUploadRequestDTO> captor = ArgumentCaptor.forClass(SongFileUploadRequestDTO.class);
            verify(service).startUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), captor.capture());
            assertThat(captor.getValue().sizeBytes(), is(800L));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            "{ \"sizeBytes\": 800 }",
            "{ \"fileName\": \"  \", \"sizeBytes\": 800 }",
            "{ \"fileName\": \"mix.wav\", \"sizeBytes\": 0 }",
            "{ \"fileName\": \"mix.wav\", \"sizeBytes\": -5 }",
        })
        void startUpload_invalidRequest_returns400AndDoesNotCallService(String body) throws Exception {
            mockMvc.perform(post(FILES_URL + "/uploads").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(service, never()).startUpload(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        void startUpload_notAudio_returns400() throws Exception {
            when(service.startUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any()))
                .thenThrow(new InvalidAudioFileException("Upload WAV, AIFF, FLAC, MP3, M4A, AAC or OGG audio"));

            mockMvc.perform(post(FILES_URL + "/uploads").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"fileName\": \"cover.png\", \"sizeBytes\": 800 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Upload WAV, AIFF, FLAC, MP3, M4A, AAC or OGG audio")));
        }

        @Test
        void confirmUpload_returns201WithTheFile() throws Exception {
            when(service.confirmUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any(SongFileConfirmRequestDTO.class)))
                .thenReturn(fileResponse);

            mockMvc.perform(post(FILES_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"key\": \"users/1000/song-files/k.wav\", \"fileName\": \"mix3.wav\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is((int) FILE_ID)));

            ArgumentCaptor<SongFileConfirmRequestDTO> captor = ArgumentCaptor.forClass(SongFileConfirmRequestDTO.class);
            verify(service).confirmUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), captor.capture());
            // No name given: the service falls back to the file name
            assertThat(captor.getValue().displayName(), is(nullValue()));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
            "{ \"fileName\": \"mix3.wav\" }",
            "{ \"key\": \"k.wav\" }",
            "{ \"key\": \"k.wav\", \"fileName\": \"mix3.wav\", \"displayName\": \"%s\" }",
        })
        void confirmUpload_invalidRequest_returns400(String template) throws Exception {
            String body = template.formatted("x".repeat(151));

            mockMvc.perform(post(FILES_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(service, never()).confirmUpload(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        void confirmUpload_missingUpload_returns400() throws Exception {
            when(service.confirmUpload(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any()))
                .thenThrow(new UploadNotFoundException());

            mockMvc.perform(post(FILES_URL).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"key\": \"users/1000/song-files/k.wav\", \"fileName\": \"mix3.wav\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("The upload was not found. Upload the file again.")));
        }
    }

    // ---------- Changing files ----------

    @Nested
    @DisplayName("PATCH, PUT /primary-file and DELETE")
    class Changes {

        @Test
        void rename_returns200() throws Exception {
            when(service.rename(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), eq(FILE_ID), any(SongFileRenameRequestDTO.class)))
                .thenReturn(fileResponse);

            mockMvc.perform(patch(FILES_URL + "/{fileId}", FILE_ID).contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"displayName\": \"Mix 3 (final)\" }"))
                .andExpect(status().isOk());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = { "{}", "{ \"displayName\": \"  \" }" })
        void rename_withoutAName_returns400(String body) throws Exception {
            mockMvc.perform(patch(FILES_URL + "/{fileId}", FILE_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

            verify(service, never()).rename(anyLong(), anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        void setPrimary_passesTheFileId() throws Exception {
            when(service.setPrimary(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any())).thenReturn(List.of(fileResponse));

            mockMvc.perform(put(SONG_URL + "/primary-file").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"fileId\": " + FILE_ID + " }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

            ArgumentCaptor<SongPrimaryFileRequestDTO> captor = ArgumentCaptor.forClass(SongPrimaryFileRequestDTO.class);
            verify(service).setPrimary(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), captor.capture());
            assertThat(captor.getValue().fileId(), is(FILE_ID));
        }

        @Test
        void setPrimary_null_clearsTheHeart() throws Exception {
            when(service.setPrimary(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), any())).thenReturn(List.of());

            mockMvc.perform(put(SONG_URL + "/primary-file").contentType(MediaType.APPLICATION_JSON)
                    .content("{ \"fileId\": null }"))
                .andExpect(status().isOk());

            ArgumentCaptor<SongPrimaryFileRequestDTO> captor = ArgumentCaptor.forClass(SongPrimaryFileRequestDTO.class);
            verify(service).setPrimary(eq(USER_ID), eq(RELEASE_ID), eq(SONG_ID), captor.capture());
            assertThat(captor.getValue().fileId(), is(nullValue()));
        }

        @Test
        void delete_returns204() throws Exception {
            mockMvc.perform(delete(FILES_URL + "/{fileId}", FILE_ID)).andExpect(status().isNoContent());

            verify(service).delete(USER_ID, RELEASE_ID, SONG_ID, FILE_ID);
        }

        @Test
        void delete_missingFile_returns404() throws Exception {
            doThrow(new SongFileNotFoundException(FILE_ID)).when(service).delete(USER_ID, RELEASE_ID, SONG_ID, FILE_ID);

            mockMvc.perform(delete(FILES_URL + "/{fileId}", FILE_ID)).andExpect(status().isNotFound());
        }
    }
}