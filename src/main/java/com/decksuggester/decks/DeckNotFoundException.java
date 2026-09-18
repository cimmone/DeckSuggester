package com.decksuggester.decks;

public class DeckNotFoundException extends RuntimeException {

    public DeckNotFoundException(String id) {
        super("Deck " + id + " was not found");
    }
}
