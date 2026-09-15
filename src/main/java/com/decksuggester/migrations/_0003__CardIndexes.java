package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Change(id = "V003-scryfall-card-lookup-indexes", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0003__CardIndexes {

    @Apply
    public void apply(MongoTemplate mongo) {
        var cards = mongo.getCollection("cards");
        cards.createIndex(new Document("id", 1), online("cards_scryfall_id"));
        cards.createIndex(new Document("oracle_id", 1), online("cards_oracle_id"));
    }

    private static IndexOptions online(String name) {
        return new IndexOptions().name(name).background(true);
    }
}
