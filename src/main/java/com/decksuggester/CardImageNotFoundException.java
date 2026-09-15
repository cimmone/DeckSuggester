package com.decksuggester;

public class CardImageNotFoundException extends RuntimeException {

    public CardImageNotFoundException() {
        super("A Scryfall image was not found for that card");
    }
}
