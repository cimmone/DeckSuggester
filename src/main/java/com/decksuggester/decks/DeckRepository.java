package com.decksuggester.decks;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeckRepository extends MongoRepository<Deck, String> {

    List<Deck> findAllByOwnerIdAndLibraryIdOrderByNameAsc(String ownerId, String libraryId);

    List<Deck> findAllByOwnerIdAndLibraryId(String ownerId, String libraryId);

    Optional<Deck> findByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);

    long deleteByIdAndOwnerIdAndLibraryId(String id, String ownerId, String libraryId);

    long deleteAllByOwnerIdAndLibraryId(String ownerId, String libraryId);
}
