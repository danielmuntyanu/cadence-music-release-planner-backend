package dev.danyil.artists;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.ArtistService;
import dev.danyil.enums.ArtistImage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping(path = "${api-endpoint}/artists")
@RequiredArgsConstructor 
public class ArtistController {
    
    private final ArtistService artistService;

    @PostMapping
    public ResponseEntity<ArtistResponseDTO> create(
            @Valid @RequestBody ArtistCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        ArtistResponseDTO created = artistService.create(request, currentUser.getId());
        
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
        
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("")
    public ResponseEntity<Page<ArtistResponseDTO>> getAll(@AuthenticationPrincipal CustomUserDetails currentUser,
            Pageable pageable) {
        return ResponseEntity.ok(artistService.getAll(currentUser.getId(), pageable));
    }

    @GetMapping("{id}")
    public ResponseEntity<ArtistResponseDTO> getById(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(artistService.getById(currentUser.getId(), id));
    }

    @PatchMapping("{id}")
    public ResponseEntity<ArtistResponseDTO> update(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id, @Valid @RequestBody ArtistUpdateRequestDTO dto) {
        return ResponseEntity.ok(artistService.update(currentUser.getId(), id, dto));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable Long id) {
        artistService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtistResponseDTO> uploadAvatar(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(artistService.uploadImage(currentUser.getId(), id, ArtistImage.AVATAR, file));
    }

    @DeleteMapping("{id}/avatar")
    public ResponseEntity<ArtistResponseDTO> removeAvatar(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(artistService.removeImage(currentUser.getId(), id, ArtistImage.AVATAR));
    }

    @PutMapping(path = "{id}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArtistResponseDTO> uploadBanner(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(artistService.uploadImage(currentUser.getId(), id, ArtistImage.BANNER, file));
    }

    @DeleteMapping("{id}/banner")
    public ResponseEntity<ArtistResponseDTO> removeBanner(@AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(artistService.removeImage(currentUser.getId(), id, ArtistImage.BANNER));
    }

}
