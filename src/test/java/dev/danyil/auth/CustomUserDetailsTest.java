package dev.danyil.auth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import dev.danyil.enums.UserRole;
import dev.danyil.users.UserEntity;

/**
 * CustomUserDetails is the security principal: it must expose exactly what
 * Spring Security needs, derived from the user entity at login time.
 */
class CustomUserDetailsTest {

    private static final long USER_ID = 1000L;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("daniel");
        user.setPasswordHash("$2a$10$hash");
        user.setRoles(new HashSet<>(Set.of(UserRole.USER)));
        user.setEnabled(true);
        user.setLocked(false);
    }

    private static Iterable<String> authorityNames(CustomUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void constructor_copiesIdentityAndPasswordHash() {
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getId(), is(USER_ID));
        assertThat(details.getUsername(), is("daniel"));
        assertThat(details.getPassword(), is("$2a$10$hash"));
    }

    @Test
    void authorities_areRolesWithRolePrefix() {
        // hasRole("ADMIN") in Spring Security checks for the "ROLE_ADMIN" authority
        user.setRoles(new HashSet<>(Set.of(UserRole.USER, UserRole.ADMIN)));

        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(authorityNames(details), containsInAnyOrder("ROLE_USER", "ROLE_ADMIN"));
    }

    @Test
    void authorities_userWithoutRoles_isEmpty() {
        user.setRoles(new HashSet<>());

        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.getAuthorities(), is(empty()));
    }

    @Test
    void activeUser_isEnabledAndNotLocked() {
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.isEnabled(), is(true));
        assertThat(details.isAccountNonLocked(), is(true));
    }

    @Test
    void disabledUser_isNotEnabled() {
        user.setEnabled(false);

        assertThat(new CustomUserDetails(user).isEnabled(), is(false));
    }

    @Test
    void lockedUser_isNotAccountNonLocked() {
        // Inverted flag: the entity stores "locked", Spring Security asks "non-locked"
        user.setLocked(true);

        assertThat(new CustomUserDetails(user).isAccountNonLocked(), is(false));
    }

    @Test
    void accountAndCredentials_neverExpire() {
        CustomUserDetails details = new CustomUserDetails(user);

        assertThat(details.isAccountNonExpired(), is(true));
        assertThat(details.isCredentialsNonExpired(), is(true));
    }

    @Test
    void details_areASnapshot_laterEntityChangesDoNotLeakIn() {
        CustomUserDetails details = new CustomUserDetails(user);

        user.setUsername("hacked");
        user.setLocked(true);
        user.getRoles().add(UserRole.ADMIN);

        assertThat(details.getUsername(), is("daniel"));
        assertThat(details.isAccountNonLocked(), is(true));
        assertThat(authorityNames(details), containsInAnyOrder("ROLE_USER"));
    }
}