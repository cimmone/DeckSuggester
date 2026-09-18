package com.decksuggester.auth;

public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
