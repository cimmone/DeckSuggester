package com.decksuggester.spellbook;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Collections;
import java.util.List;

/**
 * A Commander Spellbook combo: an ordered set of card pieces that, when
 * assembled, produce a described result. Stored in the spellbook database.
 */
@Document(collection = "combos")
public class Combo {

    @Id
    private String id;
    private String description;
    private List<ComboCard> cards;
    private List<String> results;
    private List<String> colorIdentity;

    public Combo() {
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<ComboCard> getCards() {
        return cards == null ? Collections.emptyList() : cards;
    }

    public List<String> getResults() {
        return results == null ? Collections.emptyList() : results;
    }

    public List<String> getColorIdentity() {
        return colorIdentity == null ? Collections.emptyList() : colorIdentity;
    }

    public record ComboCard(
            String scryfallId,
            String oracleId,
            String name,
            double manaValue,
            List<String> colorIdentity,
            String typeLine,
            String imageUrl,
            Double price
    ) {
        public ComboCard {
            colorIdentity = colorIdentity == null ? List.of() : List.copyOf(colorIdentity);
        }
    }
}
