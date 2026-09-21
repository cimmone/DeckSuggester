package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Renames the misleadingly Archidekt-specific {@code archidektId} field to {@code sourceId}. */
@Change(id = "V008-rename-archidekt-id-to-source-id", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0008__RenameArchidektIdToSourceId {

    @Apply
    public void apply(MongoTemplate mongo) {
        var decks = mongo.getCollection("decks");

        decks.updateMany(new Document("archidektId", new Document("$exists", true)),
                new Document("$rename", new Document("archidektId", "sourceId")));

        try {
            decks.dropIndex("decks_tenant_source_archidekt");
        } catch (RuntimeException ignored) {
            // Index may not exist yet on fresh databases; dropping is best-effort.
        }
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("source", 1).append("sourceId", 1),
                new IndexOptions().name("decks_tenant_source_id")
                        .background(true).unique(true)
                        .partialFilterExpression(new Document("ownerId",
                                new Document("$exists", true))));
    }
}
