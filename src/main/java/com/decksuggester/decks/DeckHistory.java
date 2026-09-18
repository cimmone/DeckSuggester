package com.decksuggester.decks;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A single deck-edit audit record. The history is deliberately capped at the
 * most recent {@code app.deck-history.max-entries} edits globally, with older
 * entries pruned on every write (a rolling window).
 */
@Document(collection = "deckHistory")
public class DeckHistory {

    public enum Action {
        ADD_CARD, REMOVE_CARD, IMPORT, RENAME, DELETE
    }

    @Id
    private String id;
    private String ownerId;
    private String libraryId;
    private String deckId;
    private String deckName;
    private Action action;
    private String cardName;
    private String scryfallId;
    private int quantity;
    private Instant occurredAt;

    public DeckHistory() {
    }

    public DeckHistory(String ownerId, String libraryId, String deckId, String deckName,
                       Action action, String cardName, String scryfallId, int quantity,
                       Instant occurredAt) {
        this.ownerId = ownerId;
        this.libraryId = libraryId;
        this.deckId = deckId;
        this.deckName = deckName;
        this.action = action;
        this.cardName = cardName;
        this.scryfallId = scryfallId;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public String getDeckId() {
        return deckId;
    }

    public String getDeckName() {
        return deckName;
    }

    public Action getAction() {
        return action;
    }

    public String getCardName() {
        return cardName;
    }

    public String getScryfallId() {
        return scryfallId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
