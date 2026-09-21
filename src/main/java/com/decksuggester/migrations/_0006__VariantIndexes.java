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

/** Colour-identity and commander-legality indexes for the combo recommendation query, in the spellbook database. */
@Change(id = "V006-variant-recommendation-indexes", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0006__VariantIndexes {

    private static final Logger log = LoggerFactory.getLogger(_0006__VariantIndexes.class);

    @Apply
    public void apply(SpellbookIndexTarget spellbookTarget) {
        MongoTemplate spellbook = spellbookTarget == null ? null : spellbookTarget.spellbookTemplate();
        if (spellbook == null) {
            log.warn("V006: no spellbook template available; skipping variants indexes");
            return;
        }
        MongoCollection<Document> variants = spellbook.getCollection("variants");
        variants.createIndex(new Document("identity", 1), online("variants_identity"));
        variants.createIndex(new Document("identity", 1).append("legalities.commander", 1),
                online("variants_identity_commander_legal"));
        log.info("V006: created combo recommendation indexes on {}.variants",
                spellbook.getDb().getName());
    }

    private static IndexOptions online(String name) {
        return new IndexOptions().name(name).background(true);
    }
}
