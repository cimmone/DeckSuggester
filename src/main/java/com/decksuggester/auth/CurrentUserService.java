package com.decksuggester.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public UserIdentity require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return UserIdentity.from(principal);
    }
}
