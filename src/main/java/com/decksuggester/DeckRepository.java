package com.decksuggester;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeckRepository extends MongoRepository<Deck, String> {

    List<Deck> findAllByOrderByNameAsc();
}
