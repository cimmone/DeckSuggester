package com.decksuggester.decks;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeckRepository extends MongoRepository<Deck, String> {

    List<Deck> findAllByOwnerIdAndLibraryIdOrderByNameAsc(String ownerId, String libraryId);

    List<Deck> findAllByOwnerIdAndLibraryId(String ownerId, String libraryId);

    Optional<Deck> findByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);

    long deleteByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);

    long deleteAllByOwnerIdAndLibraryId(String ownerId, String libraryId);

    /** Used by the recursive folder delete to remove decks inside a deleted folder. */
    long deleteByFolderRefInAndOwnerIdAndLibraryId(Collection<String> folderRefs,
                                                   String ownerId, String libraryId);

    long countByFolderRefInAndOwnerIdAndLibraryId(Collection<String> folderRefs,
                                                  String ownerId, String libraryId);
}
