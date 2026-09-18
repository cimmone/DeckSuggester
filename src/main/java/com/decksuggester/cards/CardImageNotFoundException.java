package com.decksuggester.cards;

public class CardImageNotFoundException extends RuntimeException {

    public CardImageNotFoundException() {
        super("A Scryfall image was not found for that card");
    }
}
