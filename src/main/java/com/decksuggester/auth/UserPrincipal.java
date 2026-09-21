package com.decksuggester.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class UserPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String username;
    private final String passwordHash;
    private final String libraryId;

    public UserPrincipal(UserAccount account) {
        this.userId = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.libraryId = account.getLibraryId();
    }

    public String userId() {
        return userId;
    }

    public String libraryId() {
        return libraryId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
