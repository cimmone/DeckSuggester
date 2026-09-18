package com.decksuggester.decks;

import java.util.List;

public record DeckCard(
        String scryfallId,
        String oracleId,
        String name,
        double manaValue,
        List<String> colors,
        List<String> colorIdentity,
        String typeLine,
        String imageUrl,
        int quantity,
        List<String> categories,
        boolean includedInDeck,
        boolean matchedScryfallCard,
        String power,
        String toughness,
        String releasedAt
) {
    public DeckCard {
        colors = colors == null ? List.of() : List.copyOf(colors);
        colorIdentity = colorIdentity == null ? List.of() : List.copyOf(colorIdentity);
        categories = categories == null ? List.of() : List.copyOf(categories);
        quantity = Math.max(quantity, 1);
    }

    /**
     * Backwards-compatible constructor for the pre-existing 12-argument shape
     * (no power / toughness / release date). Kept so existing call sites and
     * tests compile unchanged.
     */
    public DeckCard(String scryfallId, String oracleId, String name, double manaValue,
                    List<String> colors, List<String> colorIdentity, String typeLine,
                    String imageUrl, int quantity, List<String> categories,
                    boolean includedInDeck, boolean matchedScryfallCard) {
        this(scryfallId, oracleId, name, manaValue, colors, colorIdentity, typeLine, imageUrl,
                quantity, categories, includedInDeck, matchedScryfallCard, null, null, null);
    }
}
