package dev.danyil.users;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.danyil.contracts.UserService;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserLockDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
@RequestMapping(path = "${api-endpoint}/users")
@RequiredArgsConstructor 
public class UserController {

    private final UserService userService;

    @PostMapping("")
    public ResponseEntity<UserResponseDTO> registrationHandler(@Valid @RequestBody UserRequestCreateDTO dto) {
        UserResponseDTO created = userService.register(dto);

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserAdministrationResponseDTO>> getAllHandler(Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<UserAdministrationResponseDTO> getByIdHandler(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PatchMapping("{id}/make-locked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAdministrationResponseDTO> updateLockedHandler(@PathVariable Long id, @RequestBody UserLockDTO dto) {
        return ResponseEntity.ok(userService.updateLocked(id, dto.makeLocked()));
    }


}
