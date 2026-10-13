package dev.danyil.users;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.danyil.enums.UserRole;
import dev.danyil.mappers.UserMapper;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import dev.danyil.users.exceptions.CurrentUserNotFoundException;
import dev.danyil.users.exceptions.ProfileNotFoundException;
import dev.danyil.users.exceptions.UserAlreadyExistsException;
import dev.danyil.users.exceptions.UserNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String RAW_PASSWORD = "password123";

    @Mock 
    private UserProfileRepository userProfileRepository;
    @Mock 
    private UserRepository userRepository;
    @Mock 
    private UserMapper userMapper;
    @Mock 
    private PasswordEncoder passwordEncoder;

    @InjectMocks private UserServiceImpl userService;

    // ---------- helpers ----------

    private static UserEntity user(Long id, String username) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private static UserRequestCreateDTO request(String username, String email,
                                                String displayName, String bio) {
        return new UserRequestCreateDTO(username, email, RAW_PASSWORD, displayName, bio);
    }

    private UserProfileEntity captureSavedProfile() {
        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(captor.capture());
        return captor.getValue();
    }

    private UserEntity captureSavedUser() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ---------- register ----------

    @Test
    void register_savesUserWithHashedPasswordAndUserRole() {
        var req = request("daniel", "daniel@mail.com", "Dan", "bio");
        var expected = new UserResponseDTO(1L, "daniel", "Dan", "bio", null);

        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hashed");
        when(userMapper.toResponse(any(UserEntity.class), any(UserProfileEntity.class)))
                .thenReturn(expected);

        UserResponseDTO result = userService.register(req);

        UserEntity saved = captureSavedUser();
        assertThat(saved.getUsername(), is("daniel"));
        assertThat(saved.getPasswordHash(), is("hashed"));
        assertThat(saved.getRoles(), contains(UserRole.USER));
        assertThat(result, sameInstance(expected));
    }

    @Test
    void register_neverStoresRawPassword() {
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hashed");

        userService.register(request("daniel", "daniel@mail.com", null, null));

        assertThat(captureSavedUser().getPasswordHash(), not(RAW_PASSWORD));
        verify(passwordEncoder, times(1)).encode(RAW_PASSWORD);
    }

    @Test
    void register_createsProfileLinkedToUser() {
        var req = request("daniel", "daniel@mail.com", "Dan", "my bio");
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        UserProfileEntity profile = captureSavedProfile();
        assertThat(profile.getDisplayName(), is("Dan"));
        assertThat(profile.getBio(), is("my bio"));
        assertThat(profile.getUser().getUsername(), is("daniel"));
    }

    @Test
    void register_displayNameIsTrimmed() {
        var req = request("daniel", "daniel@mail.com", "  Dan  ", null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        assertThat(captureSavedProfile().getDisplayName(), is("Dan"));
    }

    @Test
    void register_blankDisplayName_fallsBackToUsername() {
        var req = request("daniel", "daniel@mail.com", "   ", null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        assertThat(captureSavedProfile().getDisplayName(), is("daniel"));
    }

    @Test
    void register_nullDisplayName_fallsBackToUsername() {
        var req = request("daniel", "daniel@mail.com", null, null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        assertThat(captureSavedProfile().getDisplayName(), is("daniel"));
    }

    @Test
    void register_trimsUsernameAndNormalizesEmail() {
        var req = request("  daniel  ", "  Daniel@Mail.COM ", null, null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        // проверки exists идут по нормализованным значениям
        verify(userRepository).existsByUsername("daniel");
        verify(userRepository).existsByEmail("daniel@mail.com");

        UserEntity saved = captureSavedUser();
        assertThat(saved.getUsername(), is("daniel"));
        assertThat(saved.getEmail(), is("daniel@mail.com"));
    }

    @Test
    void register_blankDisplayName_usesNormalizedUsername() {
        var req = request("  daniel  ", "daniel@mail.com", null, null);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        userService.register(req);

        assertThat(captureSavedProfile().getDisplayName(), is("daniel"));
    }

    @Test
    void register_usernameTaken_throwsAndSavesNothing() {
        when(userRepository.existsByUsername("daniel")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
                () -> userService.register(request("daniel", "daniel@mail.com", null, null)));

        assertThat(ex.getField(), is("username"));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void register_emailTaken_throwsAndSavesNothing() {
        when(userRepository.existsByEmail("daniel@mail.com")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
                () -> userService.register(request("daniel", "daniel@mail.com", null, null)));

        assertThat(ex.getField(), is("email"));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void register_raceOnUniqueConstraint_throwsAndSkipsProfile() {
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class,
                () -> userService.register(request("daniel", "daniel@mail.com", null, null)));

        assertThat(ex.getField(), is("username or email"));
        verifyNoInteractions(userProfileRepository);
    }

    // ---------- getCurrent ----------

    @Test
    void getCurrent_returnsMappedDto() {
        UserEntity user = user(1L, "daniel");
        UserProfileEntity profile = new UserProfileEntity();
        UserCurrentResponseDTO expected = mock(UserCurrentResponseDTO.class);

        when(userRepository.findByUsername("daniel")).thenReturn(Optional.of(user));
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(userMapper.toCurrentUser(user, profile)).thenReturn(expected);

        assertThat(userService.getCurrent("daniel"), sameInstance(expected));
    }

    @Test
    void getCurrent_userMissing_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(CurrentUserNotFoundException.class, () -> userService.getCurrent("ghost"));
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void getCurrent_profileMissing_throws() {
        when(userRepository.findByUsername("daniel")).thenReturn(Optional.of(user(1L, "daniel")));
        when(userProfileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ProfileNotFoundException.class, () -> userService.getCurrent("daniel"));
    }

    // ---------- getAll / getById ----------

    @Test
    void getAll_mapsEveryEntityInPage() {
        Pageable pageable = PageRequest.of(0, 10);
        UserEntity user = user(1L, "daniel");
        UserAdministrationResponseDTO dto = mock(UserAdministrationResponseDTO.class);

        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userMapper.toAdministrationResponse(user)).thenReturn(dto);

        Page<UserAdministrationResponseDTO> result = userService.getAll(pageable);

        assertThat(result.getContent(), contains(dto));
        assertThat(result.getTotalElements(), is(1L));
    }

    @Test
    void getAll_emptyPage_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        Page<UserAdministrationResponseDTO> result = userService.getAll(pageable);

        assertThat(result.getContent(), empty());
        verifyNoInteractions(userMapper);
    }

    @Test
    void getById_returnsMappedDto() {
        UserEntity user = user(5L, "daniel");
        UserAdministrationResponseDTO dto = mock(UserAdministrationResponseDTO.class);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userMapper.toAdministrationResponse(user)).thenReturn(dto);

        assertThat(userService.getById(5L), sameInstance(dto));
    }

    @Test
    void getById_missing_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getById(99L));
    }

    // ---------- updateLocked ----------

    @Test
    void updateLocked_true_locksUser() {
        UserEntity user = user(5L, "daniel");
        UserAdministrationResponseDTO dto = mock(UserAdministrationResponseDTO.class);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userMapper.toAdministrationResponse(user)).thenReturn(dto);

        UserAdministrationResponseDTO result = userService.updateLocked(5L, true);

        assertThat(user.isLocked(), is(true));
        assertThat(result, sameInstance(dto));
    }

    @Test
    void updateLocked_false_unlocksUser() {
        UserEntity user = user(5L, "daniel");
        user.setLocked(true);
        UserAdministrationResponseDTO dto = mock(UserAdministrationResponseDTO.class);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userMapper.toAdministrationResponse(user)).thenReturn(dto);

        userService.updateLocked(5L, false);

        assertThat(user.isLocked(), is(false));
    }

    @Test
    void updateLocked_missing_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.updateLocked(99L, true));
        verifyNoInteractions(userMapper);
    }
}
