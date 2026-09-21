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
 * Adds a compound index covering the recommendation candidate query's most
 * common shape: colour identity, then commander-format legality. V006 indexed
 * identity alone, but {@code ComboRepository} also filters on
 * {@code legalities.commander} (the default and by far the most common
 * format), so without this index that filter runs unindexed over the
 * identity-matched subset.
 */
@Change(id = "V007-variants-commander-legality-index", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0007__VariantsCommanderLegalityIndex {

    private static final Logger log = LoggerFactory.getLogger(_0007__VariantsCommanderLegalityIndex.class);

    @Apply
    public void apply(SpellbookIndexTarget spellbookTarget) {
        MongoTemplate spellbook = spellbookTarget == null ? null : spellbookTarget.spellbookTemplate();
        if (spellbook == null) {
            log.warn("V007: no spellbook template available; skipping variants commander legality index");
            return;
        }
        MongoCollection<Document> variants = spellbook.getCollection("variants");
        variants.createIndex(new Document("identity", 1)
                        .append("legalities.commander", 1),
                new IndexOptions().name("variants_identity_commander_legal")
                        .background(true));
        log.info("V007: created commander-legality candidate index on {}.variants",
                spellbook.getDb().getName());
    }
}
