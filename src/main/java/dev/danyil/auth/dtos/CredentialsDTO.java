package dev.danyil.auth.dtos;

import jakarta.validation.constraints.NotBlank;

public record CredentialsDTO(
    @NotBlank 
    String username,

    @NotBlank 
    String password
) {

}

