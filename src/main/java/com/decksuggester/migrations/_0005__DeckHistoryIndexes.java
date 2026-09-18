package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Change(id = "V005-deck-history-indexes", author = "deck-suggester", transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0005__DeckHistoryIndexes {

    @Apply
    public void apply(MongoTemplate mongo) {
        var history = mongo.getCollection("deckHistory");
        // Ordered scan of the rolling window (newest first) and per-tenant reads.
        history.createIndex(new Document("occurredAt", -1),
                new IndexOptions().name("deck_history_occurred_at").background(true));
        history.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("occurredAt", -1),
                new IndexOptions().name("deck_history_tenant_occurred_at").background(true));
    }
}
