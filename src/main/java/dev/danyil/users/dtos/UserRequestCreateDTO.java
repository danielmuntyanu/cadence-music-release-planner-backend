package dev.danyil.users.dtos;

import jakarta.validation.constraints.*;

public record UserRequestCreateDTO(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Size(max = 100) String displayName,
        @Size(max = 500) String bio
) {}
