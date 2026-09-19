package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.CardRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckEditServiceTests {

    private final DeckRepository deckRepository = mock(DeckRepository.class);
    private final CardRepository cardRepository = mock(CardRepository.class);
    private final DeckHistoryService historyService = mock(DeckHistoryService.class);
    private final DeckEditService service =
            new DeckEditService(deckRepository, cardRepository, historyService);
    private final UserIdentity alice = new UserIdentity("alice", "alice", "alice-lib");

    private Deck deck(DeckCard... cards) {
        return new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                Instant.now(), List.of(cards));
    }

    @Test
    void addingAnExistingCardIncrementsItsQuantityAndRecordsHistory() {
        DeckCard existing = new DeckCard("s1", "o1", "Sol Ring", 1.0, List.of(), List.of(),
                "Artifact", null, 1, List.of(), true, true);
        Deck deck = deck(existing);
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));
        when(cardRepository.findFirstByNameIgnoreCase("Sol Ring")).thenReturn(Optional.empty());
        when(deckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Deck result = service.addCard(alice, deck.getId(), "Sol Ring");

        assertThat(result.getCards()).hasSize(1);
        assertThat(result.getCards().get(0).quantity()).isEqualTo(2);
        verify(historyService).record(eq(alice), any(), eq(DeckHistory.Action.ADD_CARD),
                eq("Sol Ring"), any(), eq(1));
    }

    @Test
    void removingTheLastCopyDropsTheCard() {
        DeckCard existing = new DeckCard("s1", "o1", "Sol Ring", 1.0, List.of(), List.of(),
                "Artifact", null, 1, List.of(), true, true);
        Deck deck = deck(existing);
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));
        when(deckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Deck result = service.removeCard(alice, deck.getId(), "s1");

        assertThat(result.getCards()).isEmpty();
        verify(historyService).record(eq(alice), any(), eq(DeckHistory.Action.REMOVE_CARD),
                eq("Sol Ring"), eq("s1"), eq(1));
    }

    @Test
    void removingACardThatIsNotPresentFails() {
        Deck deck = deck();
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));

        assertThatThrownBy(() -> service.removeCard(alice, deck.getId(), "missing"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void searchRequiresAtLeastTwoCharacters() {
        assertThat(service.searchCards("a")).isEmpty();
    }

    @Test
    void addingACardThatIsNotAKnownScryfallCardIsRejected() {
        Deck deck = deck();
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));
        when(cardRepository.findFirstByNameIgnoreCase("Frogzilla")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addCard(alice, deck.getId(), "Frogzilla"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Frogzilla");
    }

    @Test
    void nameRegexMatchesTokensInAnyOrderAtWordBoundaries() {
        String regex = DeckEditService.buildNameRegex("bolt light");
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        assertThat(pattern.matcher("Lightning Bolt").find()).isTrue();
        assertThat(pattern.matcher("Bolt of Lightning").find()).isTrue();
        assertThat(pattern.matcher("Sol Ring").find()).isFalse();
    }
}
