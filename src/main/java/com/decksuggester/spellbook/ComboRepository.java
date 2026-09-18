package com.decksuggester.spellbook;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ComboRepository extends MongoRepository<Combo, String> {
}
