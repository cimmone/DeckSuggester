package com.decksuggester.spellbook;

import com.decksuggester.cards.Card;
import com.decksuggester.cards.CardRepository;
import com.decksuggester.decks.Deck;
import com.decksuggester.decks.DeckCard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // Full colour names (as stored on decks) mapped to their WUBRG letter, so a
    // deck identity can be compared against a combo's compact identity string.
    private static final Map<String, String> COLOR_LETTERS = Map.of(
            "white", "W", "blue", "U", "black", "B", "red", "R", "green", "G");

    private final ComboRepository comboRepository;
    private final CardRepository cardRepository;

    public RecommendationService(ComboRepository comboRepository, CardRepository cardRepository) {
        this.comboRepository = comboRepository;
        this.cardRepository = cardRepository;
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
        Set<String> identity = deckIdentityLetters(deck);

        List<Combo> combos = comboRepository.findAll();
        Map<String, Card> cardsByOracleId = loadScryfallCards(combos);

        List<Recommendation> scored = new ArrayList<>();
        for (Combo combo : combos) {
            Recommendation recommendation = score(combo, ownedOracleIds, ownedNames,
                    identity, cardsByOracleId);
            if (recommendation != null) {
                scored.add(recommendation);
            }
        }
        scored.sort(Comparator.comparingDouble(Recommendation::fitness));
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    private Map<String, Card> loadScryfallCards(List<Combo> combos) {
        Set<String> oracleIds = new HashSet<>();
        for (Combo combo : combos) {
            for (Combo.ComboCard piece : combo.getCards()) {
                if (piece.oracleId() != null) {
                    oracleIds.add(piece.oracleId());
                }
            }
        }
        Map<String, Card> byOracleId = new HashMap<>();
        if (!oracleIds.isEmpty()) {
            cardRepository.findByOracleIdIn(oracleIds)
                    .forEach(card -> byOracleId.putIfAbsent(card.getOracleId(), card));
        }
        return byOracleId;
    }

    Recommendation score(Combo combo, Set<String> ownedOracleIds, Set<String> ownedNames,
                         Set<String> identity, Map<String, Card> cardsByOracleId) {
        List<Combo.ComboCard> pieces = combo.getCards();
        if (pieces.isEmpty()) {
            return null;
        }
        int owned = 0;
        double totalMana = 0.0;
        int creatureCount = 0;
        int outsideIdentity = 0;
        List<RecommendedCard> missing = new ArrayList<>();

        for (Combo.ComboCard piece : pieces) {
            Card scryfall = piece.oracleId() == null ? null : cardsByOracleId.get(piece.oracleId());
            String typeLine = piece.typeLine() != null ? piece.typeLine()
                    : (scryfall == null ? null : scryfall.getTypeLine());
            double manaValue = scryfall != null && scryfall.getCmc() != null
                    ? scryfall.getCmc() : 0.0;
            List<String> pieceIdentity = scryfall == null ? List.of()
                    : scryfall.getColorIdentity();

            boolean isOwned = (piece.oracleId() != null && ownedOracleIds.contains(piece.oracleId()))
                    || (piece.name() != null && ownedNames.contains(normalize(piece.name())));
            if (isOwned) {
                owned++;
            }
            totalMana += manaValue;
            if (isCreature(typeLine)) {
                creatureCount++;
            }
            if (!identity.isEmpty() && !identity.containsAll(pieceIdentity)) {
                outsideIdentity++;
            }
            if (!isOwned) {
                String imageUrl = piece.imageUrl() != null ? piece.imageUrl()
                        : (scryfall == null ? null : scryfall.getImageUrl());
                missing.add(new RecommendedCard(piece.scryfallId(), piece.name(),
                        imageUrl, typeLine, manaValue, pieceExplanation(typeLine)));
            }
        }

        Double totalPrice = combo.getPrice();
        int required = pieces.size();
        double newPieceRatio = (double) (required - owned) / required;
        double fitness =
                RATIO_WEIGHT * newPieceRatio
                + IDENTITY_WEIGHT * ((double) outsideIdentity / required)
                + MANA_WEIGHT * totalMana
                + TYPE_WEIGHT * creatureCount
                + PRICE_WEIGHT * (totalPrice == null ? 0.0 : totalPrice);

        String explanation = buildExplanation(combo, owned, required, outsideIdentity,
                totalMana, creatureCount, totalPrice);
        return new Recommendation(combo.getId(), combo.getDescription(), fitness, owned,
                required, missing, explanation);
    }

    private String pieceExplanation(String typeLine) {
        return typeLine == null ? "" : typeLine;
    }

    private String buildExplanation(Combo combo, int owned, int required, int outsideIdentity,
                                    double totalMana, int creatureCount, Double totalPrice) {
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
        if (totalPrice != null && totalPrice > 0) {
            builder.append(String.format(Locale.ROOT, " Estimated cost $%.2f.", totalPrice));
        }
        return builder.toString();
    }

    private static Set<String> deckIdentityLetters(Deck deck) {
        Set<String> letters = new HashSet<>();
        for (String color : deck.getColorIdentity()) {
            if (color == null) {
                continue;
            }
            String normalized = color.trim();
            String letter = COLOR_LETTERS.get(normalized.toLowerCase(Locale.ROOT));
            if (letter != null) {
                letters.add(letter);
            } else if (normalized.length() == 1) {
                String upper = normalized.toUpperCase(Locale.ROOT);
                if (COLOR_LETTERS.containsValue(upper)) {
                    letters.add(upper);
                }
            }
        }
        return letters;
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
                                  String typeLine, double manaValue, String note) {
    }

    public record Recommendation(String comboId, String description, double fitness,
                                 int ownedPieces, int requiredPieces,
                                 List<RecommendedCard> newCards, String explanation) {
    }
}
