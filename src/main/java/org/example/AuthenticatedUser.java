package org.example;

import org.springframework.security.core.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

public class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final UUID clientId;
    private final String login;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean mfaVerified;

    public AuthenticatedUser(UUID userId, UUID clientId, String login,
                             Collection<String> roles, boolean mfaVerified) {
        this.userId = userId;
        this.clientId = clientId;
        this.login = login;
        this.authorities = roles.stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
        this.mfaVerified = mfaVerified;
    }

    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // JWT-based, no password here
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Getters for custom fields
    public UUID getUserId() { return userId; }
    public UUID getClientId() { return clientId; }
    public boolean isMfaVerified() { return mfaVerified; }
}
