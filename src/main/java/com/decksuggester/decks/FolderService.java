package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manages user-owned folders (a forest rooted at the library) and the placement of decks within them. */
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final DeckRepository deckRepository;

    public FolderService(FolderRepository folderRepository, DeckRepository deckRepository) {
        this.folderRepository = folderRepository;
        this.deckRepository = deckRepository;
    }

    public List<Folder> list(UserIdentity owner) {
        return folderRepository.findAllByOwnerIdAndLibraryId(
                owner.ownerId(), owner.libraryId());
    }

    public Folder create(UserIdentity owner, String name, String parentId) {
        if (name == null || name.trim().isBlank()) {
            throw new InvalidRequestException("Folder name cannot be blank");
        }
        if (parentId != null && !parentId.isBlank()) {
            requireFolder(owner, parentId);
        }
        int nextOrder = nextSiblingOrder(owner, parentId);
        return folderRepository.save(new Folder(owner.ownerId(), owner.libraryId(),
                name.trim(), parentId, nextOrder, Instant.now()));
    }

    public Folder rename(UserIdentity owner, String folderId, String name) {
        Folder folder = requireFolder(owner, folderId);
        if (name == null || name.trim().isBlank()) {
            throw new InvalidRequestException("Folder name cannot be blank");
        }
        folder.setName(name.trim());
        return folderRepository.save(folder);
    }

    /** Moves a folder to a new parent (null/blank for the library root) and/or sibling position. */
    public Folder move(UserIdentity owner, String folderId, String parentId, int order) {
        Folder folder = requireFolder(owner, folderId);
        String previousParent = folder.getParentId();
        String resolvedParent = parentId == null || parentId.isBlank() ? null : parentId;
        // Fetch the tenant's folders once and reuse the snapshot for every check
        // below instead of re-querying per step.
        List<Folder> folders = list(owner);
        if (resolvedParent != null) {
            if (resolvedParent.equals(folderId)) {
                throw new InvalidRequestException("A folder cannot be its own parent");
            }
            if (isDescendant(folders, resolvedParent, folderId)) {
                throw new InvalidRequestException(
                        "A folder cannot be moved into one of its own descendants");
            }
            requireFolder(owner, resolvedParent);
        }
        folder.setParentId(resolvedParent);
        int siblingCount = countSiblingsExcluding(folders, resolvedParent, folderId);
        int resolvedOrder = order < 0 ? siblingCount : Math.min(order, siblingCount);
        folder.setOrder(resolvedOrder);
        // Re-pack the sibling ordering so positions stay contiguous in the
        // destination parent...
        reorderSiblings(folders, resolvedParent, folderId, resolvedOrder);
        // ...and, when the folder actually changed parents, in the parent it
        // left behind too, otherwise that sibling group is left with a gap
        // where the moved folder's old order used to be.
        if (!java.util.Objects.equals(previousParent, resolvedParent)) {
            reorderSiblings(folders, previousParent, folderId, -1);
        }
        return folderRepository.save(folder);
    }

    /** Recursively deletes a folder, its nested folders, and every deck assigned to them. */
    public int delete(UserIdentity owner, String folderId) {
        Folder folder = requireFolder(owner, folderId);
        Set<String> doomed = collectSubtree(list(owner), folder.getId());
        doomed.add(folder.getId());
        // Decks are physical children of a folder via folderRef; deleting the
        // folder deletes the decks inside it as well, mirroring the way the UI
        // groups decks under folders.
        long decksDeleted = deckRepository.deleteByFolderRefInAndOwnerIdAndLibraryId(
                doomed, owner.ownerId(), owner.libraryId());
        folderRepository.deleteAllById(doomed);
        return doomed.size();
    }

    public long decksIn(UserIdentity owner, String folderId) {
        Set<String> subtree = collectSubtree(list(owner), folderId);
        subtree.add(folderId);
        return deckRepository.countByFolderRefInAndOwnerIdAndLibraryId(
                subtree, owner.ownerId(), owner.libraryId());
    }

    /** Moves a deck into a folder owned by the caller, or to the library root when blank. */
    public Deck moveDeck(UserIdentity owner, String deckId, String folderId) {
        Deck deck = deckRepository.findByIdAndOwnerIdAndLibraryId(
                        deckId, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new DeckNotFoundException(deckId));
        String resolved = folderId == null || folderId.isBlank() ? null : folderId;
        if (resolved != null) {
            requireFolder(owner, resolved);
        }
        deck.setFolderRef(resolved);
        return deckRepository.save(deck);
    }

    private Folder requireFolder(UserIdentity owner, String folderId) {
        return folderRepository.findByIdAndOwnerIdAndLibraryId(
                        folderId, owner.ownerId(), owner.libraryId())
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    private int nextSiblingOrder(UserIdentity owner, String parentId) {
        return countSiblingsExcluding(list(owner), parentId, null);
    }

    private int countSiblingsExcluding(List<Folder> folders, String parentId, String excludeId) {
        int count = 0;
        for (Folder folder : folders) {
            String parent = folder.getParentId();
            boolean sameParent = (parent == null && parentId == null)
                    || (parent != null && parent.equals(parentId));
            if (sameParent && !folder.getId().equals(excludeId)) {
                count++;
            }
        }
        return count;
    }

    private void reorderSiblings(List<Folder> folders, String parentId, String movedId,
                                 int movedOrder) {
        List<Folder> siblings = new ArrayList<>();
        for (Folder folder : folders) {
            String parent = folder.getParentId();
            boolean sameParent = (parent == null && parentId == null)
                    || (parent != null && parent.equals(parentId));
            if (sameParent && !folder.getId().equals(movedId)) {
                siblings.add(folder);
            }
        }
        siblings.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        int index = 0;
        for (Folder sibling : siblings) {
            if (index == movedOrder) {
                index++;
            }
            if (sibling.getOrder() != index) {
                sibling.setOrder(index);
                folderRepository.save(sibling);
            }
            index++;
        }
    }

    private Set<String> collectSubtree(List<Folder> folders, String rootId) {
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (Folder folder : folders) {
            String parent = folder.getParentId();
            if (parent != null) {
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>())
                        .add(folder.getId());
            }
        }
        Set<String> subtree = new HashSet<>();
        List<String> frontier = new ArrayList<>(childrenByParent.getOrDefault(rootId, List.of()));
        while (!frontier.isEmpty()) {
            String next = frontier.remove(0);
            if (subtree.add(next)) {
                frontier.addAll(childrenByParent.getOrDefault(next, List.of()));
            }
        }
        return subtree;
    }

    private boolean isDescendant(List<Folder> folders, String candidateId, String ancestorId) {
        return collectSubtree(folders, ancestorId).contains(candidateId);
    }
}
