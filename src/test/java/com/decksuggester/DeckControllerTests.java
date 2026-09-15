package com.decksuggester;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckControllerTests {

    private final DeckRepository deckRepository = mock(DeckRepository.class);
    private final DeckImportService importService = mock(DeckImportService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final DeckController controller =
            new DeckController(deckRepository, importService, currentUser);
    private final UserIdentity alice = new UserIdentity("alice-id", "alice", "alice-library");

    @Test
    void scopesReadsImportsUpdatesAndDeletesToTheAuthenticatedOwnersLibrary() {
        when(currentUser.require()).thenReturn(alice);
        when(deckRepository.findAllByOwnerIdAndLibraryIdOrderByNameAsc("alice-id", "alice-library"))
                .thenReturn(List.of());
        when(deckRepository.findByIdAndOwnerIdAndLibraryId("deck-1", "alice-id", "alice-library"))
                .thenReturn(Optional.of(new Deck()));
        when(deckRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        when(deckRepository.deleteByIdAndOwnerIdAndLibraryId("deck-2", "alice-id", "alice-library"))
                .thenReturn(1L);

        controller.decks();
        controller.importDecks(new DeckController.ImportRequest(
                "https://archidekt.com/folders/95630"));
        controller.update("deck-1", new DeckController.DeckUpdateRequest("Renamed", null));
        controller.delete("deck-2");
        controller.deleteAll();

        verify(deckRepository).findAllByOwnerIdAndLibraryIdOrderByNameAsc("alice-id", "alice-library");
        verify(importService).importFolder(eq("https://archidekt.com/folders/95630"), eq(alice));
        verify(deckRepository).deleteByIdAndOwnerIdAndLibraryId("deck-2", "alice-id", "alice-library");
        verify(deckRepository).deleteAllByOwnerIdAndLibraryId("alice-id", "alice-library");
    }

    @Test
    void updatingAnotherOwnersDeckIsReportedAsNotFound() {
        when(currentUser.require()).thenReturn(alice);
        when(deckRepository.findByIdAndOwnerIdAndLibraryId("someone-elses-deck",
                "alice-id", "alice-library")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.update(
                        "someone-elses-deck", new DeckController.DeckUpdateRequest("x", null)))
                .isInstanceOf(DeckNotFoundException.class);
    }
}
