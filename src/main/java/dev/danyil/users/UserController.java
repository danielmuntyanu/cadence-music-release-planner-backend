package dev.danyil.users;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.danyil.contracts.UserService;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
@RequestMapping(path = "${api-endpoint}/users")
@RequiredArgsConstructor 
public class UserController {

    private UserService userService;

    @PostMapping("")
    public ResponseEntity<Void> registrationHandler(@Valid @RequestBody UserRequestCreateDTO dto) {
        userService.register(dto);
        return ResponseEntity.noContent().build();
    }

}
