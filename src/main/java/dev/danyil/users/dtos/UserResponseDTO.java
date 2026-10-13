package dev.danyil.users.dtos;

import lombok.Builder;

@Builder 
public record UserResponseDTO(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl
) {

}
