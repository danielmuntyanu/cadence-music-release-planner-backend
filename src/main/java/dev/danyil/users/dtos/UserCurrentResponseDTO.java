package dev.danyil.users.dtos;

import java.util.Set;

import lombok.Builder;

@Builder 
public record UserCurrentResponseDTO(
    Long id,
    String username,
    String email,
    String displayName,
    String bio,
    String avatarUrl,
    Set<String> roles
) {

}
