package com.decksuggester;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends MongoRepository<Card, String> {

    List<Card> findByScryfallIdIn(Collection<String> ids);

    List<Card> findByOracleIdIn(Collection<String> ids);

    Optional<Card> findFirstByScryfallId(String id);
}
