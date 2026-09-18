package com.decksuggester.decks;

import com.decksuggester.auth.CurrentUserService;
import com.decksuggester.auth.UserIdentity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class DeckStatisticsService {

    private static final Set<String> CARD_TYPES = Set.of(
            "Artifact", "Battle", "Conspiracy", "Creature", "Dungeon",
            "Enchantment", "Instant", "Kindred", "Land", "Phenomenon", "Plane",
            "Planeswalker", "Scheme", "Sorcery", "Vanguard");

    private final DeckRepository deckRepository;
    private final CurrentUserService currentUser;

    public DeckStatisticsService(DeckRepository deckRepository, CurrentUserService currentUser) {
        this.deckRepository = deckRepository;
        this.currentUser = currentUser;
    }

    public DataStatistics statistics() {
        UserIdentity owner = currentUser.require();
        return calculate(deckRepository.findAllByOwnerIdAndLibraryId(
                owner.ownerId(), owner.libraryId()));
    }

    static DataStatistics calculate(List<Deck> decks) {
        Map<String, Long> manaValues = new TreeMap<>(Comparator.comparingDouble(Double::parseDouble));
        Map<String, Long> cardColors = new TreeMap<>();
        Map<String, Long> deckColors = new TreeMap<>();
        Map<String, Long> cardTypes = new TreeMap<>();
        long totalCards = 0;

        for (Deck deck : decks) {
            for (DeckCard card : deck.getCards()) {
                if (!card.includedInDeck()) {
                    continue;
                }
                int quantity = card.quantity();
                totalCards += quantity;
                manaValues.merge(formatManaValue(card.manaValue()), (long) quantity, Long::sum);

                List<String> colors = card.colors().isEmpty()
                        ? List.of("Colorless") : card.colors();
                colors.forEach(color -> cardColors.merge(color, (long) quantity, Long::sum));

                types(card.typeLine()).forEach(type ->
                        cardTypes.merge(type, (long) quantity, Long::sum));
            }
            List<String> identity = deck.getColorIdentity();
            if (identity.isEmpty()) {
                deckColors.merge("Colorless", 1L, Long::sum);
            } else {
                identity.forEach(color -> deckColors.merge(color, 1L, Long::sum));
            }
        }
        return new DataStatistics(decks.size(), totalCards, ordered(manaValues),
                ordered(cardColors), ordered(deckColors), ordered(cardTypes));
    }

    private static String formatManaValue(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static List<String> types(String typeLine) {
        if (typeLine == null || typeLine.isBlank()) {
            return List.of("Unknown");
        }
        String leftSide = typeLine.split("[—-]", 2)[0];
        List<String> found = new ArrayList<>();
        for (String word : leftSide.trim().split("\\s+")) {
            if (CARD_TYPES.contains(word)) {
                found.add(word);
            }
        }
        return found.isEmpty() ? List.of("Other") : found;
    }

    private static Map<String, Long> ordered(Map<String, Long> values) {
        return new LinkedHashMap<>(values);
    }

    public record DataStatistics(long totalDecks, long totalCards,
                                 Map<String, Long> manaValues,
                                 Map<String, Long> cardColors,
                                 Map<String, Long> deckColors,
                                 Map<String, Long> cardTypes) {
    }
}
