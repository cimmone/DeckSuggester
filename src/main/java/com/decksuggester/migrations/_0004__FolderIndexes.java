package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Tenant-scoped lookup and parent-traversal indexes for the folders collection. */
@Change(id = "V004-folder-indexes", author = "deck-suggester", transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0004__FolderIndexes {

    @Apply
    public void apply(MongoTemplate mongo) {
        var folders = mongo.getCollection("folders");
        folders.createIndex(new Document("ownerId", 1).append("libraryId", 1),
                online("folders_tenant"));
        folders.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("parentId", 1).append("order", 1),
                online("folders_tenant_parent_order"));
    }

    private static IndexOptions online(String name) {
        return new IndexOptions().name(name).background(true);
    }
}
