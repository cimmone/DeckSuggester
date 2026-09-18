package com.decksuggester.decks;

import com.decksuggester.auth.UserIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckHistoryServiceTests {

    private final DeckHistoryRepository repository = mock(DeckHistoryRepository.class);
    private final UserIdentity owner = new UserIdentity("alice", "alice", "alice-lib");

    @Test
    void prunesOldestEntriesWhenTheRollingCapIsExceeded() {
        DeckHistoryService service = new DeckHistoryService(repository, 300);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.count()).thenReturn(303L);
        List<DeckHistory> oldest = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            oldest.add(new DeckHistory(owner.ownerId(), owner.libraryId(), "d", "Deck",
                    DeckHistory.Action.ADD_CARD, "Card", null, 1, Instant.now()));
        }
        when(repository.findAllByOrderByOccurredAtAsc(any())).thenReturn(oldest);

        service.record(owner, new Deck(), DeckHistory.Action.ADD_CARD, "Sol Ring", "s", 1);

        ArgumentCaptor<List<DeckHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void doesNotPruneWhenBelowTheCap() {
        DeckHistoryService service = new DeckHistoryService(repository, 300);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.count()).thenReturn(10L);

        service.record(owner, new Deck(), DeckHistory.Action.IMPORT, null, null, 5);

        verify(repository, never()).deleteAll(any());
    }
}
