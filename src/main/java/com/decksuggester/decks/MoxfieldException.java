package com.decksuggester.decks;

public class MoxfieldException extends RuntimeException {

    public MoxfieldException(String message) {
        super(message);
    }

    public MoxfieldException(String message, Throwable cause) {
        super(message, cause);
    }
}
