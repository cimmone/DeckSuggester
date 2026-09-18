package com.decksuggester.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class AccountPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String accountId;
    private final String username;
    private final String passwordHash;
    private final String libraryId;

    public AccountPrincipal(UserAccount account) {
        this.accountId = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.libraryId = account.getLibraryId();
    }

    public String accountId() {
        return accountId;
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
