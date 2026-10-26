package dev.danyil.releases;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.danyil.auth.CustomUserDetails;
import dev.danyil.contracts.ReleaseService;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseDatesRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleaseGenresRequestDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping(path = "${api-endpoint}/releases")
@RequiredArgsConstructor 
public class ReleaseController {
    
    private final ReleaseService releaseService;

    @PostMapping("")
    public ResponseEntity<ReleaseResponseDTO> create(
            @Valid @RequestBody ReleaseCreateRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        ReleaseResponseDTO created = releaseService.create(request, currentUser.getId());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("")
    public ResponseEntity<Page<ReleaseResponseDTO>> getAll(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @ModelAttribute ReleaseFilterDTO filter,
            @PageableDefault(size = 24, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(releaseService.getAll(currentUser.getId(), filter, pageable));
    }

    @GetMapping("{id}")
    public ResponseEntity<ReleaseResponseDTO> getById(
            @AuthenticationPrincipal CustomUserDetails currectUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(releaseService.getById(currectUser.getId(), id));
    }

    @PatchMapping("{id}")
    public ResponseEntity<ReleaseResponseDTO> update(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReleaseUpdateRequestDTO request) {
        return ResponseEntity.ok(releaseService.update(currentUser.getId(), id, request));
    }


    @PatchMapping("{id}/phase")
    public ResponseEntity<ReleaseResponseDTO> updatePhase(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReleasePhaseUpdateRequestDTO request) {
        return ResponseEntity.ok(releaseService.updatePhase(currentUser.getId(), id, request));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails currentUser,
        @PathVariable Long id) {
        releaseService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "{id}/artwork", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReleaseResponseDTO> uploadArtwork(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(releaseService.uploadArtwork(currentUser.getId(), id, file));
    }

    @DeleteMapping("{id}/artwork")
    public ResponseEntity<ReleaseResponseDTO> removeArtwork(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(releaseService.removeArtwork(currentUser.getId(), id));
    }

    @PutMapping("{id}/genres")
    public ResponseEntity<ReleaseResponseDTO> updateGenres(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReleaseGenresRequestDTO request) {
        return ResponseEntity.ok(releaseService.updateGenres(currentUser.getId(), id, request));
    }

    @PutMapping("{id}/dates")
    public ResponseEntity<ReleaseResponseDTO> updateDates(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReleaseDatesRequestDTO request) {
        return ResponseEntity.ok(releaseService.updateDates(currentUser.getId(), id, request));
    }

}
