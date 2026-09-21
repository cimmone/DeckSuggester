package com.decksuggester.decks;

import java.util.List;

/**
 * A single card entry on a deck, denormalised with the slice of Scryfall
 * metadata the UI needs to render and analyse the deck without re-querying
 * the card catalog.
 */
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
        String releasedAt,
        String backImageUrl,
        String layout) {

    public DeckCard {
        colors = colors == null ? List.of() : List.copyOf(colors);
        colorIdentity = colorIdentity == null ? List.of() : List.copyOf(colorIdentity);
        categories = categories == null ? List.of() : List.copyOf(categories);
        quantity = Math.max(quantity, 1);
    }

    /** Backwards-compatible 12-argument constructor (no power/toughness/release/back image/layout). */
    public DeckCard(String scryfallId, String oracleId, String name, double manaValue,
                    List<String> colors, List<String> colorIdentity, String typeLine,
                    String imageUrl, int quantity, List<String> categories,
                    boolean includedInDeck, boolean matchedScryfallCard) {
        this(scryfallId, oracleId, name, manaValue, colors, colorIdentity, typeLine, imageUrl,
                quantity, categories, includedInDeck, matchedScryfallCard, null, null, null,
                null, null);
    }

    /** Backwards-compatible 15-argument constructor (no back image/layout). */
    public DeckCard(String scryfallId, String oracleId, String name, double manaValue,
                    List<String> colors, List<String> colorIdentity, String typeLine,
                    String imageUrl, int quantity, List<String> categories,
                    boolean includedInDeck, boolean matchedScryfallCard,
                    String power, String toughness, String releasedAt) {
        this(scryfallId, oracleId, name, manaValue, colors, colorIdentity, typeLine, imageUrl,
                quantity, categories, includedInDeck, matchedScryfallCard, power, toughness,
                releasedAt, null, null);
    }

    /** {@code true} when the card has a second face and should render a flip control. */
    public boolean isFlippable() {
        return backImageUrl != null && !backImageUrl.isBlank();
    }
}
