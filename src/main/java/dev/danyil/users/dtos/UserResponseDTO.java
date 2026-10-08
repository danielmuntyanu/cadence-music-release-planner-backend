package dev.danyil.users.dtos;

public record UserResponseDTO(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl
) {

}
