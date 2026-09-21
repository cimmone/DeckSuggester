package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderServiceTests {

    private final FolderRepository folderRepository = mock(FolderRepository.class);
    private final DeckRepository deckRepository = mock(DeckRepository.class);
    private final FolderService service = new FolderService(folderRepository, deckRepository);
    private final UserIdentity owner = new UserIdentity("alice", "alice", "alice-lib");

    @Test
    void creatingAFolderAppendsItAfterExistingSiblings() {
        when(folderRepository.findAllByOwnerIdAndLibraryId("alice", "alice-lib"))
                .thenReturn(List.of(folder("f1", null, 0), folder("f2", null, 1)));
        when(folderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Folder created = service.create(owner, "New", null);

        assertThat(created.getName()).isEqualTo("New");
        assertThat(created.getParentId()).isNull();
        assertThat(created.getOrder()).isEqualTo(2);
    }

    @Test
    void movingAFolderIntoItsOwnDescendantIsRejected() {
        Folder parent = folder("parent", null, 0);
        Folder child = folder("child", "parent", 0);
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("parent", "alice", "alice-lib"))
                .thenReturn(Optional.of(parent));
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("child", "alice", "alice-lib"))
                .thenReturn(Optional.of(child));
        when(folderRepository.findAllByOwnerIdAndLibraryId("alice", "alice-lib"))
                .thenReturn(List.of(parent, child));

        assertThatThrownBy(() -> service.move(owner, "parent", "child", 0))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void movingAFolderToAnotherParentRepacksTheGapLeftBehind() {
        Folder a = folder("a", null, 0);
        Folder b = folder("b", null, 1);
        Folder c = folder("c", null, 2);
        Folder dest = folder("dest", null, 3);
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("b", "alice", "alice-lib"))
                .thenReturn(Optional.of(b));
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("dest", "alice", "alice-lib"))
                .thenReturn(Optional.of(dest));
        when(folderRepository.findAllByOwnerIdAndLibraryId("alice", "alice-lib"))
                .thenReturn(List.of(a, b, c, dest));
        when(folderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Folder moved = service.move(owner, "b", "dest", -1);

        assertThat(moved.getParentId()).isEqualTo("dest");
        assertThat(moved.getOrder()).isEqualTo(0);
        // Root's remaining siblings (a, c) must be repacked to close the gap
        // "b" left behind, or a later "move up"/"move down" on "c" would be a
        // no-op since its order would still point one position too far right.
        assertThat(a.getOrder()).isEqualTo(0);
        assertThat(c.getOrder()).isEqualTo(1);
    }

    @Test
    void deletingAFolderRemovesItsSubtreeAndEveryDeckInside() {
        Folder root = folder("root", null, 0);
        Folder child = folder("child", "root", 0);
        Folder grandchild = folder("grandchild", "child", 0);
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("root", "alice", "alice-lib"))
                .thenReturn(Optional.of(root));
        when(folderRepository.findAllByOwnerIdAndLibraryId("alice", "alice-lib"))
                .thenReturn(List.of(root, child, grandchild));
        when(deckRepository.deleteByFolderRefInAndOwnerIdAndLibraryId(
                anyCollection(), eq("alice"), eq("alice-lib"))).thenReturn(4L);

        int deleted = service.delete(owner, "root");

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(deckRepository).deleteByFolderRefInAndOwnerIdAndLibraryId(
                captor.capture(), eq("alice"), eq("alice-lib"));
        assertThat(captor.getValue()).containsExactlyInAnyOrder("root", "child", "grandchild");
        verify(folderRepository).deleteAllById(org.mockito.ArgumentMatchers
                .<Set<String>>argThat(set -> set.containsAll(List.of("root", "child", "grandchild"))));
    }

    @Test
    void movingADeckToTheLibraryRootClearsTheFolderRef() {
        Deck deck = new Deck(1L, "D", null, "url", 0L, "root", 0L, "folder",
                Instant.now(), List.of());
        deck.setFolderRef("f1");
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));
        when(deckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Deck moved = service.moveDeck(owner, deck.getId(), null);

        assertThat(moved.getFolderRef()).isNull();
    }

    @Test
    void movingADeckIntoAnUnknownFolderIsRejected() {
        Deck deck = new Deck(1L, "D", null, "url", 0L, "root", 0L, "folder",
                Instant.now(), List.of());
        when(deckRepository.findByIdAndOwnerIdAndLibraryId(deck.getId(), "alice", "alice-lib"))
                .thenReturn(Optional.of(deck));
        when(folderRepository.findByIdAndOwnerIdAndLibraryId("missing", "alice", "alice-lib"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.moveDeck(owner, deck.getId(), "missing"))
                .isInstanceOf(FolderNotFoundException.class);
    }

    private Folder folder(String id, String parentId, int order) {
        Folder folder = new Folder("alice", "alice-lib", id, parentId, order, Instant.now());
        try {
            java.lang.reflect.Field f = Folder.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(folder, id);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        return folder;
    }
}
