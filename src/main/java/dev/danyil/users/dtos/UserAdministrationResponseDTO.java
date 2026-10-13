package dev.danyil.users.dtos;

import java.time.Instant;
import java.util.Set;

import lombok.Builder;

@Builder 
public record UserAdministrationResponseDTO(
    Long id,
    String username,
    String email,
    Set<String> roles,
    boolean enabled,
    boolean locked,
    Instant createdAt
) {

}
