package com.decksuggester;

public record UserIdentity(String ownerId, String username, String libraryId) {

    public static UserIdentity from(AccountPrincipal principal) {
        return new UserIdentity(principal.accountId(), principal.getUsername(),
                principal.libraryId());
    }
}
