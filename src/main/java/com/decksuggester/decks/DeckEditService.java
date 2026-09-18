package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.Card;
import com.decksuggester.cards.CardRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mutating deck operations exposed by the deck editor: searching for cards by
 * name, and adding/removing individual cards from a deck. Every mutation is
 * written to the rolling deck-edit history.
 */
@Service
public class DeckEditService {

    private static final Map<String, String> COLOR_NAMES = Map.of(
            "W", "White", "U", "Blue", "B", "Black", "R", "Red", "G", "Green");

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DeckHistoryService historyService;

    public DeckEditService(DeckRepository deckRepository, CardRepository cardRepository,
                           DeckHistoryService historyService) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.historyService = historyService;
    }

    public List<CardSuggestion> searchCards(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String regex = Pattern.quote(query.trim());
        return cardRepository.searchByName(regex, PageRequest.of(0, 10)).stream()
                .map(card -> new CardSuggestion(card.getId(), card.getName(),
                        card.getTypeLine(), card.getImageUrl()))
                .toList();
    }

    public Deck addCard(UserIdentity owner, String deckId, String cardName) {
        Deck deck = requireDeck(owner, deckId);
        if (cardName == null || cardName.isBlank()) {
            throw new InvalidRequestException("A card name is required");
        }
        Card card = cardRepository.findFirstByNameIgnoreCase(cardName.trim()).orElse(null);

        List<DeckCard> cards = new ArrayList<>(deck.getCards());
        int existingIndex = indexOfIncluded(cards, card, cardName);
        String recordedName;
        String scryfallId;
        if (existingIndex >= 0) {
            DeckCard existing = cards.get(existingIndex);
            cards.set(existingIndex, withQuantity(existing, existing.quantity() + 1, true));
            recordedName = existing.name();
            scryfallId = existing.scryfallId();
        } else if (card != null) {
            cards.add(new DeckCard(card.getId(), card.getOracleId(), card.getName(),
                    card.getCmc() == null ? 0.0 : card.getCmc(),
                    normalizeColors(card.getColors()), normalizeColors(card.getColorIdentity()),
                    card.getTypeLine(), card.getImageUrl(), 1, List.of(), true, true,
                    card.getPower(), card.getToughness(), card.getReleasedAt()));
            recordedName = card.getName();
            scryfallId = card.getId();
        } else {
            cards.add(new DeckCard(null, null, cardName.trim(), 0.0, List.of(), List.of(),
                    null, null, 1, List.of(), true, false));
            recordedName = cardName.trim();
            scryfallId = null;
        }
        deck.setCards(cards);
        Deck saved = deckRepository.save(deck);
        historyService.record(owner, saved, DeckHistory.Action.ADD_CARD, recordedName,
                scryfallId, 1);
        return saved;
    }

    public Deck removeCard(UserIdentity owner, String deckId, String scryfallIdOrName) {
        Deck deck = requireDeck(owner, deckId);
        List<DeckCard> cards = new ArrayList<>(deck.getCards());
        int index = indexOfCard(cards, scryfallIdOrName);
        if (index < 0) {
            throw new InvalidRequestException("That card is not in the deck");
        }
        DeckCard existing = cards.get(index);
        String recordedName = existing.name();
        String scryfallId = existing.scryfallId();
        if (existing.quantity() > 1) {
            cards.set(index, withQuantity(existing, existing.quantity() - 1,
                    existing.includedInDeck()));
        } else {
            cards.remove(index);
        }
        deck.setCards(cards);
        Deck saved = deckRepository.save(deck);
        historyService.record(owner, saved, DeckHistory.Action.REMOVE_CARD, recordedName,
                scryfallId, 1);
        return saved;
    }

    private Deck requireDeck(UserIdentity owner, String deckId) {
        return deckRepository.findByIdAndOwnerIdAndLibraryId(
                        deckId, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new DeckNotFoundException(deckId));
    }

    private static int indexOfIncluded(List<DeckCard> cards, Card card, String name) {
        for (int i = 0; i < cards.size(); i++) {
            DeckCard current = cards.get(i);
            boolean matches = card != null && card.getId() != null
                    && card.getId().equals(current.scryfallId());
            if (!matches && name != null) {
                matches = name.trim().equalsIgnoreCase(current.name());
            }
            if (matches) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfCard(List<DeckCard> cards, String scryfallIdOrName) {
        for (int i = 0; i < cards.size(); i++) {
            DeckCard current = cards.get(i);
            if (scryfallIdOrName.equals(current.scryfallId())
                    || scryfallIdOrName.equalsIgnoreCase(current.name())) {
                return i;
            }
        }
        return -1;
    }

    private static DeckCard withQuantity(DeckCard card, int quantity, boolean included) {
        return new DeckCard(card.scryfallId(), card.oracleId(), card.name(), card.manaValue(),
                card.colors(), card.colorIdentity(), card.typeLine(), card.imageUrl(),
                quantity, card.categories(), included, card.matchedScryfallCard());
    }

    private static List<String> normalizeColors(List<String> colors) {
        return colors.stream()
                .map(color -> COLOR_NAMES.getOrDefault(color, color))
                .toList();
    }

    public record CardSuggestion(String scryfallId, String name, String typeLine,
                                 String imageUrl) {
    }
}
