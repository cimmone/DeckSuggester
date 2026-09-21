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

    /** Case-insensitive batch lookup by exact name, for resolving a pasted decklist in one round trip. */
    @Query(value = "{ 'name': { $in: ?0 } }", collation = "{ locale: 'en', strength: 2 }")
    List<Card> findByNameInIgnoreCase(Collection<String> names);

    /** Case-insensitive substring search over card names, for the "add card" autocomplete. */
    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<Card> searchByName(String nameRegex, Pageable pageable);
}
