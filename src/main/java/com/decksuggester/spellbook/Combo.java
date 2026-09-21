package com.decksuggester.spellbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Commander Spellbook combo, mapped from the "variants" documents exported from
 * <a href="https://json.commanderspellbook.com/variants.json">Commander Spellbook</a>
 * into the {@code variants} collection: the cards it {@code uses}, the features it
 * {@code produces}, a colour {@code identity} (compact WUBRG letters, e.g. {@code "RGWU"}),
 * a human {@code description}, and aggregate {@code prices}.
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
    private ComboLegalities legalities;
    private Integer popularity;

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

    /** Compact WUBRG identity string (e.g. {@code "RGW"}); colourless combos use the literal {@code "C"}, not an empty string. */
    public String getIdentity() {
        return identity == null ? "" : identity;
    }

    public Prices getPrices() {
        return prices;
    }

    /** Per-format legality flags exported by Commander Spellbook, used via {@link ComboLegalities#commander()} to prune ineligible combos before scoring. */
    public ComboLegalities getLegalities() {
        return legalities;
    }

    /** Commander Spellbook popularity score; higher is more popular. */
    public Integer getPopularity() {
        return popularity;
    }

    /** The cards required by this combo, flattened from {@link #getUses()} with each piece's required quantity. */
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

    /** The features this combo produces, used to explain why it is worth adding. */
    public List<String> getResults() {
        List<String> results = new ArrayList<>();
        for (Produce produce : getProduces()) {
            if (produce.feature() != null && produce.feature().name() != null) {
                results.add(produce.feature().name());
            }
        }
        return results;
    }

    /** Best available aggregate price (TCGplayer, then Card Kingdom, then Cardmarket), or {@code null} when unknown. */
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

        /** The Scryfall id, derived from the image URL's UUID segment (e.g. {@code .../normal/front/9/8/<uuid>.jpg?...}). */
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

    /** Per-format legality flags mirrored from the Commander Spellbook export. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComboLegalities(
            boolean commander,
            boolean vintage,
            boolean legacy,
            boolean modern,
            boolean pauper,
            boolean oathbreaker) {
    }

    /**
     * A flattened combo piece used by the recommendation scorer.
     */
    public record ComboCard(String scryfallId, String oracleId, String name,
                            String typeLine, String imageUrl, int quantity) {
    }
}
