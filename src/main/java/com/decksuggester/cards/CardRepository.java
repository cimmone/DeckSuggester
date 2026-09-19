package com.decksuggester.cards;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends MongoRepository<Card, String> {

    List<Card> findByScryfallIdIn(Collection<String> ids);

    List<Card> findByOracleIdIn(Collection<String> ids);

    Optional<Card> findFirstByScryfallId(String id);

    Optional<Card> findFirstByNameIgnoreCase(String name);

    /**
     * Case-insensitive substring search over card names, used to populate the
     * "add card" autocomplete. The supplied {@code nameRegex} is a
     * pre-built (already escaped) regular expression; the caller supplies a
     * Pageable so the number of suggestions can be capped.
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Card> searchByName(String nameRegex, Pageable pageable);
}
