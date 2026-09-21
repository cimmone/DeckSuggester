package com.decksuggester;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;

/**
 * Excludes Spring Boot's own Mongo autoconfiguration, which would otherwise re-scan the
 * whole application for repositories and register an unqualified {@code MongoDatabaseFactory}
 * bean, conflicting with the three explicitly-wired databases in {@link MongoConfig} and
 * {@link RepositoryConfig}.
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
