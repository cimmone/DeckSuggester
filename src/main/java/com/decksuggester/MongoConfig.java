package com.decksuggester;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * The application now spans three logical Mongo databases:
 *
 * <ul>
 *   <li><b>scryfall</b> &mdash; the read-only Scryfall bulk card data.</li>
 *   <li><b>suggester</b> &mdash; user accounts, imported decks and the rolling
 *       deck-edit history. This data was historically stored in the scryfall
 *       database; {@code _0004__MoveUserDeckDataToSuggester} migrates it across
 *       without orphaning anything.</li>
 *   <li><b>spellbook</b> &mdash; Commander Spellbook combo/recommendation
 *       data.</li>
 * </ul>
 *
 * A single {@link MongoClient} is shared across all three databases (they live
 * on the same host/credentials); each database gets its own
 * {@link MongoDatabaseFactory} and {@link MongoTemplate}. The suggester
 * template is {@link Primary} so the migration runner and any un-qualified
 * injection point keeps working.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClient mongoClient(@Value("${mongodb.host}") String host) {
        return MongoClients.create(new ConnectionString(host));
    }

    private MongoDatabaseFactory factory(MongoClient client, String database) {
        return new SimpleMongoClientDatabaseFactory(client, database);
    }

    // --- scryfall (cards) -------------------------------------------------

    @Bean
    public MongoDatabaseFactory scryfallDatabaseFactory(
            MongoClient client, @Value("${mongodb.scryfall-database}") String database) {
        return factory(client, database);
    }

    @Bean
    public MongoTemplate scryfallMongoTemplate(MongoDatabaseFactory scryfallDatabaseFactory) {
        return new MongoTemplate(scryfallDatabaseFactory);
    }

    // --- suggester (users, decks, history) --------------------------------

    @Bean
    @Primary
    public MongoDatabaseFactory suggesterDatabaseFactory(
            MongoClient client, @Value("${mongodb.suggester-database}") String database) {
        return factory(client, database);
    }

    @Bean
    public MongoTemplate suggesterMongoTemplate(MongoDatabaseFactory suggesterDatabaseFactory) {
        return new MongoTemplate(suggesterDatabaseFactory);
    }

    // The migration runner injects a plain MongoTemplate; keep that pointed at
    // the suggester database (where users/decks/history now live).
    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoTemplate suggesterMongoTemplate) {
        return suggesterMongoTemplate;
    }

    // --- spellbook (combo recommendations) --------------------------------

    @Bean
    public MongoDatabaseFactory spellbookDatabaseFactory(
            MongoClient client, @Value("${mongodb.spellbook-database}") String database) {
        return factory(client, database);
    }

    @Bean
    public MongoTemplate spellbookMongoTemplate(MongoDatabaseFactory spellbookDatabaseFactory) {
        return new MongoTemplate(spellbookDatabaseFactory);
    }

}
