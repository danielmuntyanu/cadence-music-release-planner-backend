package dev.danyil.songs;

import java.net.URI;
import java.util.List;

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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.SongService;
import dev.danyil.songs.dtos.SongOrderRequestDTO;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;
import dev.danyil.songs.dtos.SongStatusUpdateRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "${api-endpoint}/releases/{releaseId}/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    @GetMapping("")
    public ResponseEntity<List<SongResponseDTO>> getAll(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId) {
        return ResponseEntity.ok(songService.getAll(currentUser.getId(), releaseId));
    }

    @PostMapping
    public ResponseEntity<SongResponseDTO> create(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @Valid @RequestBody SongRequestDTO request) {

        SongResponseDTO created = songService.create(currentUser.getId(), releaseId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("{songId}")
    public ResponseEntity<SongResponseDTO> getById(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @PathVariable Long songId) {
        return ResponseEntity.ok(songService.getById(currentUser.getId(), releaseId, songId));
    }

    @PutMapping("{songId}")
    public ResponseEntity<SongResponseDTO> replace(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @PathVariable Long songId,
            @Valid @RequestBody SongRequestDTO request) {
        return ResponseEntity.ok(songService.replace(currentUser.getId(), releaseId, songId, request));
    }

    @PatchMapping("{songId}/status")
    public ResponseEntity<SongResponseDTO> updateStatus(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @PathVariable Long songId,
            @Valid @RequestBody SongStatusUpdateRequestDTO request) {
        return ResponseEntity.ok(songService.updateStatus(currentUser.getId(), releaseId, songId, request));
    }

    @DeleteMapping("{songId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @PathVariable Long songId) {
        songService.delete(currentUser.getId(), releaseId, songId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("order")
    public ResponseEntity<List<SongResponseDTO>> reorder(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long releaseId,
            @Valid @RequestBody SongOrderRequestDTO request) {
        return ResponseEntity.ok(songService.reorder(currentUser.getId(), releaseId, request));
    }
}