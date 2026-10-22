package dev.danyil.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import dev.danyil.enums.UserRole;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final long USER_ID = 1000L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private static UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$hash");
        user.setRoles(new HashSet<>(Set.of(UserRole.USER)));
        user.setEnabled(true);
        return user;
    }

    @Test
    void loadUserByUsername_existingUser_returnsDetailsForThatUser() {
        when(userRepository.findByUsername("daniel")).thenReturn(Optional.of(user("daniel")));

        CustomUserDetails details = userDetailsService.loadUserByUsername("daniel");

        assertThat(details.getId(), is(USER_ID));
        assertThat(details.getUsername(), is("daniel"));
        assertThat(details.getPassword(), is("$2a$10$hash"));
    }

    @Test
    void loadUserByUsername_passesUsernameUnchangedToRepository() {
        when(userRepository.findByUsername("Daniel")).thenReturn(Optional.of(user("Daniel")));

        userDetailsService.loadUserByUsername("Daniel");

        verify(userRepository).findByUsername("Daniel");
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("ghost"));
    }

    @Test
    void loadUserByUsername_unknownUser_messageDoesNotEchoUsername() {
        // A generic message avoids confirming which usernames were tried if it ever reaches logs or responses
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("ghost"));

        assertThat(exception.getMessage(), not(containsString("ghost")));
    }
}