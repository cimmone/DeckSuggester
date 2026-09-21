package com.decksuggester.decks;

import java.util.Map;

/** Maps Magic color letters to their full names, e.g. {@code "W"} to {@code "White"}. */
final class CardColors {

    static final Map<String, String> NAMES = Map.of(
            "W", "White", "U", "Blue", "B", "Black", "R", "Red", "G", "Green");

    private CardColors() {
    }
}
