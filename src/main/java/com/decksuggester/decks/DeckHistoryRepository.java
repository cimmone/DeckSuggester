package com.decksuggester.decks;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeckHistoryRepository extends MongoRepository<DeckHistory, String> {

    List<DeckHistory> findAllByOwnerIdAndLibraryIdOrderByOccurredAtDesc(
            String ownerId, String libraryId, Pageable pageable);

    List<DeckHistory> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<DeckHistory> findAllByOrderByOccurredAtAsc(Pageable pageable);

    long count();
}
