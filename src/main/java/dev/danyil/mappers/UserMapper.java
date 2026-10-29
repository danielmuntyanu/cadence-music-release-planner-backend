package dev.danyil.mappers;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.danyil.media.MediaUrlResolver;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserProfileEntity;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class UserMapper {

    private final MediaUrlResolver media;

    public UserResponseDTO toResponse(UserEntity user, UserProfileEntity profile) {
        return UserResponseDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(profile.getDisplayName())
            .avatarUrl(profile.getAvatarUrl())
            .bio(profile.getBio())
        .build();
    }

    public UserCurrentResponseDTO toCurrentUser(UserEntity user, UserProfileEntity profile) {
        return UserCurrentResponseDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(profile.getDisplayName())
            .avatarUrl(media.resolve(profile.getAvatarKey(), profile.getAvatarUrl()))
            .bio(profile.getBio())
            .roles(
                user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet())
            )
        .build();
    }

    public UserAdministrationResponseDTO toAdministrationResponse(UserEntity user) {
        return UserAdministrationResponseDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .enabled(user.isEnabled())
            .locked(user.isLocked())
            .createdAt(user.getCreatedAt())
            .roles(
                user.getRoles().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet())
            )
        .build();
    }


}
