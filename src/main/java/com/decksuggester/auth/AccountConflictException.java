package com.decksuggester.auth;

public class AccountConflictException extends RuntimeException {

    public AccountConflictException(String message) {
        super(message);
    }
}
