package com.decksuggester.auth;

public record UserIdentity(String ownerId, String username, String libraryId) {

    public static UserIdentity from(UserPrincipal principal) {
        return new UserIdentity(principal.userId(), principal.getUsername(),
                principal.libraryId());
    }
}
