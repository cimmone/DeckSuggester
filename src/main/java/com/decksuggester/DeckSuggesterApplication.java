package com.decksuggester;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;

/**
 * Every Mongo bean (clients, database factories, templates, repositories) is
 * wired explicitly in {@link MongoConfig} and {@link RepositoryConfig} for
 * this app's three databases (scryfall, suggester, spellbook), with no
 * {@code @Primary} candidate. Spring Boot's own Mongo autoconfiguration is
 * excluded because it otherwise fights that scheme in two ways:
 *
 * <ul>
 *   <li>{@link DataMongoRepositoriesAutoConfiguration} would re-scan the whole
 *       application package and re-register every repository against a single
 *       template, silently pointing {@code CardRepository} at the wrong
 *       database.</li>
 *   <li>{@link DataMongoAutoConfiguration} declares a {@code mappingMongoConverter}
 *       bean that autowires a single, unqualified {@code MongoDatabaseFactory};
 *       with three factory beans and none marked {@code @Primary}, that bean
 *       fails to start. Nothing in this app uses it &mdash; each
 *       {@code MongoTemplate} in {@link MongoConfig} builds its own converter
 *       internally.</li>
 * </ul>
 */
@SpringBootApplication(exclude = {
        DataMongoRepositoriesAutoConfiguration.class,
        DataMongoAutoConfiguration.class
})
@EnableFlamingock(stages = @Stage(name = "indexes",
        location = "com.decksuggester.migrations"))
public class DeckSuggesterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeckSuggesterApplication.class, args);
    }

}
