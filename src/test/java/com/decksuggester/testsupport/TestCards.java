package com.decksuggester.testsupport;

import com.decksuggester.cards.Card;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Card} documents for seeding a real (embedded) Mongo instance
 * in end-to-end tests. {@link Card} has no public constructor or setters
 * (it is populated by Jackson/Spring Data field binding from the Scryfall
 * export), so this sets its private fields via reflection.
 */
public final class TestCards {

    private TestCards() {
    }

    public static Card card(String scryfallId, String oracleId, String name, double cmc,
                            String typeLine, List<String> colors, List<String> colorIdentity,
                            String power, String toughness, String releasedAt,
                            Map<String, String> legalities, String imageUrl) {
        Card card = new Card();
        set(card, "scryfallId", scryfallId);
        set(card, "oracleId", oracleId);
        set(card, "name", name);
        set(card, "cmc", cmc);
        set(card, "typeLine", typeLine);
        set(card, "colors", colors);
        set(card, "colorIdentity", colorIdentity);
        set(card, "power", power);
        set(card, "toughness", toughness);
        set(card, "releasedAt", releasedAt);
        set(card, "legalities", legalities);
        if (imageUrl != null) {
            set(card, "imageUris", Map.of("normal", imageUrl));
        }
        return card;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
