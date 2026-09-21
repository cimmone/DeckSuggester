package com.decksuggester.auth;

public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
