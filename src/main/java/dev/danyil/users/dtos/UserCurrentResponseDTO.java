package dev.danyil.users.dtos;

import java.util.Set;

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
