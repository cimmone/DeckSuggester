package com.decksuggester;

public class AccountConflictException extends RuntimeException {

    public AccountConflictException(String message) {
        super(message);
    }
}
