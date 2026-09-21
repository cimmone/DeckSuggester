package com.decksuggester.migrations;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Adds the indexes required by the new folder-management, Moxfield/manual
 * deck-import and optimised combo-recommendation features.
 *
 * <ul>
 *   <li><b>folders</b> (suggester db): a tenant-scoped lookup and a parent
 *       traversal index so listing and recursive delete never scan the whole
 *       collection.</li>
 *   <li><b>decks</b> (suggester db): a tenant + folderRef index for folder
 *       contents and a replacement of the legacy unique archidekt-id index with
 *       a source-aware unique index, so Moxfield and manual decks (which reuse
 *       the deterministic id scheme with their own stable external ids) do not
 *       collide on a zero {@code archidektId}.</li>
 *   <li><b>variants</b> (spellbook db): a colour-identity index so the
 *       recommendation candidate query filters on the Mongo side instead of
 *       loading the whole collection into memory. The variants
 *       collection lives in the separate spellbook database, so its indexes are
 *       created through the {@link SpellbookIndexTarget} dependency (Flamingock
 *       runs changes against the suggester template, and a plain MongoDatabase
 *       factory does not expose a cross-database client through its proxy).</li>
 * </ul>
 */
@Change(id = "V006-folder-combo-and-source-indexes", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0006__FolderComboAndSourceIndexes {

    private static final Logger log = LoggerFactory.getLogger(_0006__FolderComboAndSourceIndexes.class);

    @Apply
    public void apply(MongoTemplate mongo, SpellbookIndexTarget spellbookTarget) {
        var decks = mongo.getCollection("decks");
        var folders = mongo.getCollection("folders");

        // --- folders (suggester db) ----------------------------------------
        folders.createIndex(new Document("ownerId", 1).append("libraryId", 1),
                online("folders_tenant"));
        folders.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("parentId", 1).append("order", 1),
                online("folders_tenant_parent_order"));

        // --- decks (suggester db) ------------------------------------------
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("folderRef", 1),
                online("decks_tenant_folder_ref"));
        // Replace the legacy unique (ownerId, libraryId, archidektId) index with
        // a source-aware one so non-Archidekt decks can coexist with Archidekt
        // decks under the same tenant.
        try {
            decks.dropIndex("decks_tenant_archidekt");
        } catch (RuntimeException ignored) {
            // Index may not exist yet on fresh databases; dropping is best-effort.
        }
        decks.createIndex(new Document("ownerId", 1).append("libraryId", 1)
                        .append("source", 1).append("archidektId", 1),
                new IndexOptions().name("decks_tenant_source_archidekt")
                        .background(true).unique(true)
                        .partialFilterExpression(new Document("ownerId",
                                new Document("$exists", true))));

        // --- variants (spellbook db) ---------------------------------------
        MongoTemplate spellbook = spellbookTarget == null ? null
                : spellbookTarget.spellbookTemplate();
        if (spellbook == null) {
            log.warn("V006: no spellbook template available; skipping variants indexes");
            return;
        }
        MongoCollection<Document> variants = spellbook.getCollection("variants");
        variants.createIndex(new Document("identity", 1), online("variants_identity"));
        log.info("V006: created combo recommendation indexes on {}.variants",
                spellbook.getDb().getName());
    }

    private static IndexOptions online(String name) {
        return new IndexOptions().name(name).background(true);
    }
}
