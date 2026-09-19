package com.decksuggester;

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
    private final boolean enabled;

    public MongoMigrationRunner(@Qualifier("suggesterMongoTemplate") MongoTemplate mongoTemplate,
                                @Value("${app.migrations.enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
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
