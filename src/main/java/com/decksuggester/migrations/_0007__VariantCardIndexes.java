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

/** Index on the cards a combo uses, so the "already in deck" / "partially in deck" recommendation filters don't scan every variant. */
@Change(id = "V007-variant-card-indexes", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0007__VariantCardIndexes {

    private static final Logger log = LoggerFactory.getLogger(_0007__VariantCardIndexes.class);

    @Apply
    public void apply(SpellbookIndexTarget spellbookTarget) {
        MongoTemplate spellbook = spellbookTarget == null ? null : spellbookTarget.spellbookTemplate();
        if (spellbook == null) {
            log.warn("V007: no spellbook template available; skipping variants card index");
            return;
        }
        MongoCollection<Document> variants = spellbook.getCollection("variants");
        variants.createIndex(new Document("uses.card.oracleId", 1),
                new IndexOptions().name("variants_uses_card_oracle_id").background(true));
        log.info("V007: created uses.card.oracleId index on {}.variants",
                spellbook.getDb().getName());
    }
}
