package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Tenant-scoped lookup and folder-membership indexes for the decks collection. */
@Change(id = "V002-tenant-deck-indexes", author = "deck-suggester", transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0002__DeckIndexes {

    @Apply
    public void apply(MongoTemplate mongo) {
        var decks = mongo.getCollection("decks");
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("name", 1),
                online("decks_tenant_name"));
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("rootFolderId", 1),
                online("decks_tenant_root_folder"));
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("folderRef", 1),
                online("decks_tenant_folder_ref"));
        // Unique per tenant so a re-import of the same external deck can't
        // duplicate; scoped by source since Archidekt/Moxfield/manual ids are
        // drawn from independent id spaces.
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("source", 1).append("sourceId", 1),
                new IndexOptions().name("decks_tenant_source_id").background(true)
                        .unique(true)
                        .partialFilterExpression(new Document("ownerId",
                                new Document("$exists", true))));
    }

    private static IndexOptions online(String name) {
        return new IndexOptions().name(name).background(true);
    }
}
