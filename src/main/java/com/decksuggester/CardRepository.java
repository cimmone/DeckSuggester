package com.decksuggester;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface CardRepository extends MongoRepository<Card, String> {

    List<Card> findByScryfallIdIn(Collection<String> ids);

    List<Card> findByOracleIdIn(Collection<String> ids);
}
