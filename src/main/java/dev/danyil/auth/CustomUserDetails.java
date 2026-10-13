package dev.danyil.auth;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import dev.danyil.users.UserEntity;

public class CustomUserDetails implements UserDetails {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean locked;

    public CustomUserDetails(UserEntity user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        this.enabled = user.isEnabled();
        this.locked = user.isLocked();
    }

    public Long getId() { return id; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
