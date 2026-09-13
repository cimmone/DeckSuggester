package com.decksuggester;

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
        boolean matchedScryfallCard
) {
    public DeckCard {
        colors = colors == null ? List.of() : List.copyOf(colors);
        colorIdentity = colorIdentity == null ? List.of() : List.copyOf(colorIdentity);
        categories = categories == null ? List.of() : List.copyOf(categories);
        quantity = Math.max(quantity, 1);
    }
}
