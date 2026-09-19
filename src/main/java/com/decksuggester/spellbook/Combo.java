package com.decksuggester.spellbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Commander Spellbook combo. This maps the "variants" documents exported from
 * <a href="https://json.commanderspellbook.com/variants.json">Commander
 * Spellbook</a> and loaded into the {@code variants} collection of the
 * spellbook database (see the Dockerfile import step).
 *
 * <p>Each variant lists the cards it {@code uses}, the features it
 * {@code produces}, a colour {@code identity} (a compact string of WUBRG
 * letters, e.g. {@code "RGWU"}), a human {@code description}, and aggregate
 * {@code prices}.</p>
 */
@Document(collection = "variants")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Combo {

    @Id
    private String id;
    private String description;
    private List<Use> uses;
    private List<Produce> produces;
    private String identity;
    private Prices prices;

    public Combo() {
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<Use> getUses() {
        return uses == null ? Collections.emptyList() : uses;
    }

    public List<Produce> getProduces() {
        return produces == null ? Collections.emptyList() : produces;
    }

    /**
     * The colour identity as a compact WUBRG letter string (e.g. {@code "RGW"}).
     * Colourless combos have an empty identity ({@code "C"} is normalised away).
     */
    public String getIdentity() {
        return identity == null ? "" : identity;
    }

    public Prices getPrices() {
        return prices;
    }

    /**
     * The cards required by this combo. Convenience view over {@link #getUses()}
     * that exposes the piece card details plus the required quantity.
     */
    public List<ComboCard> getCards() {
        List<ComboCard> cards = new ArrayList<>();
        for (Use use : getUses()) {
            Card card = use.card();
            if (card == null) {
                continue;
            }
            cards.add(new ComboCard(card.scryfallId(), card.oracleId(), card.name(),
                    card.typeLine(), card.imageUrl(), Math.max(1, use.quantity())));
        }
        return cards;
    }

    /**
     * The features this combo produces, used to explain why it is worth adding.
     */
    public List<String> getResults() {
        List<String> results = new ArrayList<>();
        for (Produce produce : getProduces()) {
            if (produce.feature() != null && produce.feature().name() != null) {
                results.add(produce.feature().name());
            }
        }
        return results;
    }

    /**
     * The best available aggregate combo price, in the order Commander
     * Spellbook prefers (TCGplayer, then Card Kingdom, then Cardmarket), or
     * {@code null} when no price is known.
     */
    public Double getPrice() {
        if (prices == null) {
            return null;
        }
        Double price = parse(prices.tcgplayer());
        if (price == null) {
            price = parse(prices.cardkingdom());
        }
        if (price == null) {
            price = parse(prices.cardmarket());
        }
        return price;
    }

    private static Double parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed <= 0 ? null : parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Use(Card card, int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(
            @Field("id") Long spellbookId,
            String name,
            String oracleId,
            String typeLine,
            @Field("imageUriFrontNormal") String imageUrl) {

        /**
         * Commander Spellbook keys its own card ids; the Scryfall id is derived
         * from the image URL when present. The image URL contains the Scryfall
         * UUID (e.g. {@code .../normal/front/9/8/<uuid>.jpg?...}).
         */
        public String scryfallId() {
            if (imageUrl == null) {
                return null;
            }
            int lastSlash = imageUrl.lastIndexOf('/');
            if (lastSlash < 0 || lastSlash + 1 >= imageUrl.length()) {
                return null;
            }
            String file = imageUrl.substring(lastSlash + 1);
            int dot = file.indexOf('.');
            String uuid = dot < 0 ? file : file.substring(0, dot);
            return uuid.isBlank() ? null : uuid;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Produce(Feature feature, int quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prices(String tcgplayer, String cardkingdom, String cardmarket) {
    }

    /**
     * A flattened combo piece used by the recommendation scorer.
     */
    public record ComboCard(String scryfallId, String oracleId, String name,
                            String typeLine, String imageUrl, int quantity) {
    }
}
