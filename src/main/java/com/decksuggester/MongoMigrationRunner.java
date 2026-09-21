package com.decksuggester;

import com.decksuggester.migrations.CardIndexTarget;
import com.decksuggester.migrations.SpellbookIndexTarget;
import io.flamingock.community.Flamingock;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.targetsystem.mongodb.springdata.MongoDBSpringDataTargetSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoMigrationRunner.class);

    private final MongoTemplate mongoTemplate;
    private final MongoTemplate scryfallMongoTemplate;
    private final MongoTemplate spellbookMongoTemplate;
    private final boolean enabled;

    public MongoMigrationRunner(@Qualifier("suggesterMongoTemplate") MongoTemplate mongoTemplate,
                                @Qualifier("scryfallMongoTemplate") MongoTemplate scryfallMongoTemplate,
                                @Qualifier("spellbookMongoTemplate") MongoTemplate spellbookMongoTemplate,
                                @Value("${app.migrations.enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.scryfallMongoTemplate = scryfallMongoTemplate;
        this.spellbookMongoTemplate = spellbookMongoTemplate;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationIsAvailable() {
        if (!enabled) {
            log.info("Mongo migrations are disabled");
            return;
        }
        Thread.ofVirtual().name("mongo-online-migrations").start(this::run);
    }

    void run() {
        try {
            MongoDBSpringDataTargetSystem target =
                    new MongoDBSpringDataTargetSystem("mongodb", mongoTemplate);
            MongoDBSyncAuditStore auditStore = MongoDBSyncAuditStore.from(target)
                    .withAuditRepositoryName("flamingockChangeLog")
                    .withLockRepositoryName("flamingockLock")
                    .withAutoCreate(true);
            Flamingock.builder()
                    .setAuditStore(auditStore)
                    .addTargetSystem(target)
                    .addDependency(MongoTemplate.class, mongoTemplate)
                    // The scryfall and spellbook templates are handed to changes
                    // through wrapper types so they don't clash with the suggester
                    // template already registered under MongoTemplate.
                    .addDependency(CardIndexTarget.class,
                            new CardIndexTarget(scryfallMongoTemplate))
                    .addDependency(SpellbookIndexTarget.class,
                            new SpellbookIndexTarget(spellbookMongoTemplate))
                    .setServiceIdentifier("deck-suggester")
                    .setThrowExceptionIfCannotObtainLock(false)
                    .build()
                    .run();
        } catch (RuntimeException exception) {
            // Index creation is deliberately decoupled from startup availability.
            // Flamingock records failures and ALWAYS_RETRY changes resume on a
            // later startup without taking the web application down.
            log.error("Online Mongo migrations did not complete", exception);
        }
    }
}
