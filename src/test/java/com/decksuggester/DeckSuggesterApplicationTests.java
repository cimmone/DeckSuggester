package com.decksuggester;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.migrations.enabled=false")
class DeckSuggesterApplicationTests {

    @Test
    void contextLoads() {
    }

}
