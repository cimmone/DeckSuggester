package com.decksuggester.spellbook;

import com.decksuggester.decks.Deck;
import com.decksuggester.decks.DeckCard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ranks Commander Spellbook combos by how well they fit a specific deck and
 * surfaces the best combo pieces the deck does not yet own.
 *
 * <p>Fitness is scored (lower is better) from five weighted signals:</p>
 * <ol>
 *   <li>The ratio of new pieces required to total pieces &mdash; the fewer new
 *       cards required, the better.</li>
 *   <li>Colour-identity alignment with the deck's commander &mdash; pieces
 *       outside the identity are penalised.</li>
 *   <li>Total mana value of the combo &mdash; cheaper is better.</li>
 *   <li>Card types &mdash; Creatures are more fragile than
 *       Artifact/Land/Enchantment/Instant/Sorcery pieces, so creature-heavy
 *       combos are penalised.</li>
 *   <li>Monetary cost of the combo &mdash; cheaper is better.</li>
 * </ol>
 */
@Service
public class RecommendationService {

    static final Set<String> ROBUST_TYPES = Set.of(
            "artifact", "land", "enchantment", "instant", "sorcery");

    private static final double RATIO_WEIGHT = 100.0;
    private static final double IDENTITY_WEIGHT = 40.0;
    private static final double MANA_WEIGHT = 2.0;
    private static final double TYPE_WEIGHT = 8.0;
    private static final double PRICE_WEIGHT = 0.5;

    private final ComboRepository comboRepository;

    public RecommendationService(ComboRepository comboRepository) {
        this.comboRepository = comboRepository;
    }

    public List<Recommendation> recommend(Deck deck, int limit) {
        Set<String> ownedOracleIds = new HashSet<>();
        Set<String> ownedNames = new HashSet<>();
        for (DeckCard card : deck.getCards()) {
            if (!card.includedInDeck()) {
                continue;
            }
            if (card.oracleId() != null) {
                ownedOracleIds.add(card.oracleId());
            }
            if (card.name() != null) {
                ownedNames.add(normalize(card.name()));
            }
        }
        Set<String> identity = new HashSet<>(deck.getColorIdentity());

        List<Recommendation> scored = new ArrayList<>();
        for (Combo combo : comboRepository.findAll()) {
            Recommendation recommendation = score(combo, ownedOracleIds, ownedNames, identity);
            if (recommendation != null) {
                scored.add(recommendation);
            }
        }
        scored.sort(Comparator.comparingDouble(Recommendation::fitness));
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    Recommendation score(Combo combo, Set<String> ownedOracleIds, Set<String> ownedNames,
                         Set<String> identity) {
        List<Combo.ComboCard> pieces = combo.getCards();
        if (pieces.isEmpty()) {
            return null;
        }
        int owned = 0;
        double totalMana = 0.0;
        double totalPrice = 0.0;
        int creatureCount = 0;
        int outsideIdentity = 0;
        List<RecommendedCard> missing = new ArrayList<>();

        for (Combo.ComboCard piece : pieces) {
            boolean isOwned = (piece.oracleId() != null && ownedOracleIds.contains(piece.oracleId()))
                    || (piece.name() != null && ownedNames.contains(normalize(piece.name())));
            if (isOwned) {
                owned++;
            }
            totalMana += piece.manaValue();
            totalPrice += piece.price() == null ? 0.0 : piece.price();
            if (isCreature(piece.typeLine())) {
                creatureCount++;
            }
            if (!identity.isEmpty()
                    && !identity.containsAll(piece.colorIdentity())) {
                outsideIdentity++;
            }
            if (!isOwned) {
                missing.add(new RecommendedCard(piece.scryfallId(), piece.name(),
                        piece.imageUrl(), piece.typeLine(), piece.manaValue(),
                        piece.price(), pieceExplanation(piece)));
            }
        }

        int required = pieces.size();
        double newPieceRatio = (double) (required - owned) / required;
        double fitness =
                RATIO_WEIGHT * newPieceRatio
                + IDENTITY_WEIGHT * ((double) outsideIdentity / required)
                + MANA_WEIGHT * totalMana
                + TYPE_WEIGHT * creatureCount
                + PRICE_WEIGHT * totalPrice;

        String explanation = buildExplanation(combo, owned, required, outsideIdentity,
                totalMana, creatureCount, totalPrice);
        return new Recommendation(combo.getId(), combo.getDescription(), fitness, owned,
                required, missing, explanation);
    }

    private String pieceExplanation(Combo.ComboCard piece) {
        StringBuilder builder = new StringBuilder();
        if (piece.typeLine() != null) {
            builder.append(piece.typeLine());
        }
        if (piece.price() != null) {
            builder.append(builder.isEmpty() ? "" : " \u00b7 ")
                    .append(String.format(Locale.ROOT, "$%.2f", piece.price()));
        }
        return builder.toString();
    }

    private String buildExplanation(Combo combo, int owned, int required, int outsideIdentity,
                                    double totalMana, int creatureCount, double totalPrice) {
        StringBuilder builder = new StringBuilder();
        String result = combo.getResults().isEmpty() ? combo.getDescription()
                : String.join("; ", combo.getResults());
        if (result != null && !result.isBlank()) {
            builder.append(result).append(". ");
        }
        builder.append("You already own ").append(owned).append(" of ").append(required)
                .append(" pieces");
        if (owned == required) {
            builder.append(" \u2014 this combo is fully assembled");
        } else {
            builder.append(", needing ").append(required - owned).append(" more");
        }
        builder.append(". Total mana value ").append(trimNumber(totalMana)).append('.');
        if (outsideIdentity > 0) {
            builder.append(' ').append(outsideIdentity)
                    .append(" piece(s) fall outside your commander's colour identity.");
        } else {
            builder.append(" Every piece fits your commander's colour identity.");
        }
        if (creatureCount == 0) {
            builder.append(" Non-creature pieces make this combo resilient to removal.");
        } else {
            builder.append(' ').append(creatureCount)
                    .append(" creature piece(s) make it more fragile.");
        }
        if (totalPrice > 0) {
            builder.append(String.format(Locale.ROOT, " Estimated cost $%.2f.", totalPrice));
        }
        return builder.toString();
    }

    private static boolean isCreature(String typeLine) {
        return typeLine != null && typeLine.toLowerCase(Locale.ROOT).contains("creature");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public record RecommendedCard(String scryfallId, String name, String imageUrl,
                                  String typeLine, double manaValue, Double price,
                                  String note) {
    }

    public record Recommendation(String comboId, String description, double fitness,
                                 int ownedPieces, int requiredPieces,
                                 List<RecommendedCard> newCards, String explanation) {
    }
}
