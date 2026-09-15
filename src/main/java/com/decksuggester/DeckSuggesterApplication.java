package com.decksuggester;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFlamingock(stages = @Stage(name = "indexes",
        location = "com.decksuggester.migrations"))
public class DeckSuggesterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeckSuggesterApplication.class, args);
    }

}
