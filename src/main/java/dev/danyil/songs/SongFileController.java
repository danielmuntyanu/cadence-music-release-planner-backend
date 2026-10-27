package dev.danyil.songs;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.SongFileService;
import dev.danyil.songs.dtos.SongFileConfirmRequestDTO;
import dev.danyil.songs.dtos.SongFileRenameRequestDTO;
import dev.danyil.songs.dtos.SongFileResponseDTO;
import dev.danyil.songs.dtos.SongFileUploadRequestDTO;
import dev.danyil.songs.dtos.SongFileUploadResponseDTO;
import dev.danyil.songs.dtos.SongPrimaryFileRequestDTO;
import dev.danyil.storage.dtos.DownloadLinkDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "${api-endpoint}/releases/{releaseId}/songs/{songId}")
@RequiredArgsConstructor
public class SongFileController {

    private final SongFileService songFileService;

    @GetMapping("/files")
    public ResponseEntity<List<SongFileResponseDTO>> getAll(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId) {
        return ResponseEntity.ok(songFileService.getAll(currentUser.getId(), releaseId, songId));
    }

    /** Step 1 of an upload: get a signed PUT URL. */
    @PostMapping("/files/uploads")
    public ResponseEntity<SongFileUploadResponseDTO> startUpload(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId,
            @Valid @RequestBody SongFileUploadRequestDTO request) {
        return ResponseEntity.ok(songFileService.startUpload(currentUser.getId(), releaseId, songId, request));
    }

    /** Step 3 of an upload: the file is in storage, create the record. */
    @PostMapping("/files")
    public ResponseEntity<SongFileResponseDTO> confirmUpload(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId,
            @Valid @RequestBody SongFileConfirmRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(songFileService.confirmUpload(currentUser.getId(), releaseId, songId, request));
    }

    @PatchMapping("/files/{fileId}")
    public ResponseEntity<SongFileResponseDTO> rename(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId, @PathVariable Long fileId,
            @Valid @RequestBody SongFileRenameRequestDTO request) {
        return ResponseEntity.ok(songFileService.rename(currentUser.getId(), releaseId, songId, fileId, request));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<DownloadLinkDTO> downloadLink(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId, @PathVariable Long fileId) {
        return ResponseEntity.ok(songFileService.downloadLink(currentUser.getId(), releaseId, songId, fileId));
    }

    @PutMapping("/primary-file")
    public ResponseEntity<List<SongFileResponseDTO>> setPrimary(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId,
            @RequestBody SongPrimaryFileRequestDTO request) {
        return ResponseEntity.ok(songFileService.setPrimary(currentUser.getId(), releaseId, songId, request));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId, @PathVariable Long songId, @PathVariable Long fileId) {
        songFileService.delete(currentUser.getId(), releaseId, songId, fileId);
        return ResponseEntity.noContent().build();
    }
}