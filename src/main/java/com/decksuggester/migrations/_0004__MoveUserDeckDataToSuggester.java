package com.decksuggester.migrations;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves the user, deck and deck-history collections out of the scryfall
 * database (where they historically lived alongside the card data) and into the
 * dedicated suggester database.
 *
 * <p>The copy is idempotent and non-destructive: documents are upserted into
 * the suggester database keyed by {@code _id}, so re-running the change never
 * duplicates or orphans data, and the source collections are left intact as a
 * safety net.</p>
 */
@Change(id = "V004-move-user-deck-data-to-suggester", author = "deck-suggester",
        transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0004__MoveUserDeckDataToSuggester {

    private static final Logger log =
            LoggerFactory.getLogger(_0004__MoveUserDeckDataToSuggester.class);

    private static final List<String> COLLECTIONS = List.of("users", "decks", "deckHistory");

    @Apply
    public void apply(MongoTemplate suggesterTemplate,
                      @Value("${mongodb.scryfall-database}") String scryfallDatabase,
                      @Value("${mongodb.suggester-database}") String suggesterDatabase) {
        MongoDatabase suggester = suggesterTemplate.getDb();
        if (suggesterDatabase.equals(scryfallDatabase)) {
            log.info("Scryfall and suggester databases are the same; nothing to move");
            return;
        }
        MongoDatabase scryfall = suggesterTemplate.getMongoDatabaseFactory()
                .getMongoDatabase(scryfallDatabase);

        for (String collectionName : COLLECTIONS) {
            copy(scryfall, suggester, collectionName);
        }
    }

    private void copy(MongoDatabase source, MongoDatabase target, String collectionName) {
        MongoCollection<Document> from = source.getCollection(collectionName);
        MongoCollection<Document> to = target.getCollection(collectionName);
        List<Document> batch = new ArrayList<>();
        long moved = 0;
        for (Document document : from.find()) {
            Object id = document.get("_id");
            to.replaceOne(new Document("_id", id), document,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true));
            moved++;
        }
        log.info("Copied {} documents from scryfall.{} to suggester.{}",
                moved, collectionName, collectionName);
        batch.clear();
    }
}
