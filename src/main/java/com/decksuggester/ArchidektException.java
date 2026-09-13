package com.decksuggester;

public class ArchidektException extends RuntimeException {

    public ArchidektException(String message) {
        super(message);
    }

    public ArchidektException(String message, Throwable cause) {
        super(message, cause);
    }
}
