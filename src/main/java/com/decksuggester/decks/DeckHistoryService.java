package com.decksuggester.decks;

import com.decksuggester.auth.UserIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Records deck edits and keeps only the most recent {@code maxEntries} of them
 * (a rolling window). Older records are pruned on every write.
 */
@Service
public class DeckHistoryService {

    private final DeckHistoryRepository repository;
    private final int maxEntries;

    public DeckHistoryService(DeckHistoryRepository repository,
                              @Value("${app.deck-history.max-entries:300}") int maxEntries) {
        this.repository = repository;
        this.maxEntries = Math.max(maxEntries, 1);
    }

    public DeckHistory record(UserIdentity owner, Deck deck, DeckHistory.Action action,
                              String cardName, String scryfallId, int quantity) {
        DeckHistory entry = new DeckHistory(owner.ownerId(), owner.libraryId(),
                deck == null ? null : deck.getId(), deck == null ? null : deck.getName(),
                action, cardName, scryfallId, quantity, Instant.now());
        DeckHistory saved = repository.save(entry);
        prune();
        return saved;
    }

    /**
     * Removes the oldest entries so the collection never exceeds the configured
     * cap. Implemented as an explicit trim rather than a TTL so the window is
     * strictly count-based (the most recent 300 edits), independent of age.
     */
    void prune() {
        long total = repository.count();
        long excess = total - maxEntries;
        if (excess <= 0) {
            return;
        }
        Pageable oldest = PageRequest.of(0, (int) Math.min(excess, Integer.MAX_VALUE));
        List<DeckHistory> toDelete = repository.findAllByOrderByOccurredAtAsc(oldest);
        repository.deleteAll(toDelete);
    }

    public List<DeckHistory> recentForOwner(UserIdentity owner) {
        return repository.findAllByOwnerIdAndLibraryIdOrderByOccurredAtDesc(
                owner.ownerId(), owner.libraryId(), PageRequest.of(0, maxEntries));
    }

    public int maxEntries() {
        return maxEntries;
    }
}
