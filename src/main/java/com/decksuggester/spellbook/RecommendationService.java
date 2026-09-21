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
 * surfaces the best combo pieces the deck does not yet own: {@link ComboRepository#findCandidates}
 * prefilters a capped candidate set on the Mongo side by colour identity and
 * combo-level format legality, then this service scores each candidate in
 * memory (per-card Scryfall legality, ownership, fitness) and sorts by
 * fitness, with {@link FilterMode} selecting whether owned, partially-owned,
 * or all legal combos are returned.
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

    /** Maximum number of combo candidates pulled from Mongo per request. */
    private static final int CANDIDATE_CAP = 400;

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
        return recommend(deck, limit, FilterMode.LEGAL, "commander");
    }

    public List<Recommendation> recommend(Deck deck, int limit, FilterMode filter, String format) {
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
        // Signature cards count as "owned" for combo matching too, so a combo
        // built around the commander is recognised as partially assembled.
        for (DeckCard card : deck.getSignatureCards()) {
            if (card.oracleId() != null) {
                ownedOracleIds.add(card.oracleId());
            }
            if (card.name() != null) {
                ownedNames.add(normalize(card.name()));
            }
        }
        Set<String> identity = deckIdentityLetters(deck);
        String identityRegex = identityRegex(identity);
        String requestedFormat = normalizeFormat(format);
        FilterMode mode = filter == null ? FilterMode.LEGAL : filter;

        // Mongo-side: filter by colour identity + combo-level format legality.
        // For OWNED/PARTIAL, further restrict to combos touching a card the
        // deck owns and return every match uncapped, since that set is
        // inherently small; a candidate cap only applies to the general LEGAL
        // browse, where it is a coarse prefilter to keep the set small and is
        // not the final fitness order.
        List<Combo> candidates = mode == FilterMode.LEGAL
                ? comboRepository.findCandidates(identityRegex, requestedFormat, CANDIDATE_CAP)
                : comboRepository.findCandidatesForOwnedCards(identityRegex, requestedFormat,
                        ownedOracleIds);
        Map<String, Card> cardsByOracleId = loadScryfallCards(candidates);

        List<Recommendation> scored = new ArrayList<>();
        for (Combo combo : candidates) {
            Recommendation recommendation = score(combo, ownedOracleIds, ownedNames,
                    identity, cardsByOracleId, requestedFormat);
            if (recommendation == null) {
                continue;
            }
            if (mode == FilterMode.OWNED && recommendation.ownedPieces()
                    != recommendation.requiredPieces()) {
                continue;
            }
            if (mode == FilterMode.PARTIAL && recommendation.ownedPieces() <= 0) {
                continue;
            }
            scored.add(recommendation);
        }
        // The fitness score computed above (which Mongo cannot compute, since
        // it needs per-card Scryfall data and this deck's ownership) is the
        // sort key for what the caller sees.
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
                         Set<String> identity, Map<String, Card> cardsByOracleId,
                         String format) {
        List<Combo.ComboCard> pieces = combo.getCards();
        if (pieces.isEmpty()) {
            return null;
        }
        String requestedFormat = normalizeFormat(format);
        int owned = 0;
        double totalMana = 0.0;
        int creatureCount = 0;
        int outsideIdentity = 0;
        List<RecommendedCard> missing = new ArrayList<>();

        for (Combo.ComboCard piece : pieces) {
            Card scryfall = piece.oracleId() == null ? null : cardsByOracleId.get(piece.oracleId());
            // Format legality is determined per card from the Scryfall legality
            // metadata; a combo whose pieces are not legal in the deck's format
            // is ineligible, regardless of colour identity. A piece that does
            // not resolve to a local Scryfall card has no legality signal at
            // all, so it is treated as ineligible rather than silently passed.
            if (scryfall == null || !scryfall.isLegal(requestedFormat)) {
                return null;
            }
            String typeLine = piece.typeLine() != null ? piece.typeLine() : scryfall.getTypeLine();
            double manaValue = scryfall.getCmc() != null ? scryfall.getCmc() : 0.0;
            List<String> pieceIdentity = scryfall.getColorIdentity();
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
                        : scryfall.getImageUrl();
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

    /** Anchored regex matching combo identities that are a subset of the deck's identity (colourless combos, stored as {@code "C"}, always match). */
    public static String identityRegex(Set<String> identity) {
        if (identity.isEmpty()) {
            return "^C$";
        }
        String letters = identity.stream().sorted().reduce("", String::concat);
        return "^([" + letters + "]*|C)$";
    }

    private static String normalizeFormat(String format) {
        // Lowercased so it agrees with both the Mongo-side legality field name
        // (legalities.<format>, itself lowercase) and Card#isLegal, which does
        // an exact-case lookup against Scryfall's lowercase legality keys.
        return format == null || format.isBlank() ? "commander"
                : format.trim().toLowerCase(Locale.ROOT);
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

    /** Ownership-based filter for the recommendations radio button. */
    public enum FilterMode {
        /** Combos that are already fully assembled in the deck. */
        OWNED,
        /** Combos with at least one piece already in the deck (incl. fully owned). */
        PARTIAL,
        /** Every combo legal for the deck (incl. fully owned or partially owned). */
        LEGAL;

        public static FilterMode fromQuery(String value) {
            if (value == null || value.isBlank()) {
                return LEGAL;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "owned", "in-deck" -> OWNED;
                case "partial", "partially" -> PARTIAL;
                default -> LEGAL;
            };
        }
    }

    public record RecommendedCard(String scryfallId, String name, String imageUrl,
                                  String typeLine, double manaValue, String note) {
    }

    public record Recommendation(String comboId, String description, double fitness,
                                 int ownedPieces, int requiredPieces,
                                 List<RecommendedCard> newCards, String explanation) {
    }
}
