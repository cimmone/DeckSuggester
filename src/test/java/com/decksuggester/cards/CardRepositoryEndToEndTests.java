package com.decksuggester.cards;

import com.decksuggester.testsupport.EmbeddedMongoSupport;
import com.decksuggester.testsupport.TestCards;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.migrations.enabled=false")
class CardRepositoryEndToEndTests {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb.host", EmbeddedMongoSupport::connectionString);
    }

    @Autowired
    private CardRepository cardRepository;

    @Test
    void findOnePerOracleIdReturnsASingleProjectedPrintingPerCard() {
        Map<String, String> legal = Map.of("commander", "legal");
        cardRepository.saveAll(List.of(
                TestCards.card("ring-lea", "oracle-ring", "Sol Ring", 1.0, "Artifact",
                        List.of(), List.of(), null, null, "1993-08-05", legal, "https://img/ring-lea"),
                TestCards.card("ring-c21", "oracle-ring", "Sol Ring", 1.0, "Artifact",
                        List.of(), List.of(), null, null, "2021-04-23", legal, "https://img/ring-c21"),
                TestCards.card("ring-cmm", "oracle-ring", "Sol Ring", 1.0, "Artifact",
                        List.of(), List.of(), null, null, "2023-11-17", legal, "https://img/ring-cmm"),
                TestCards.card("bolt-1", "oracle-bolt", "Lightning Bolt", 1.0, "Instant",
                        List.of("R"), List.of("R"), null, null, "2020-01-01", legal, null)));

        List<Card> cards = cardRepository.findOnePerOracleId(
                List.of("oracle-ring", "oracle-bolt", "oracle-unknown"));

        assertEquals(2, cards.size());
        Card ring = cards.stream().filter(card -> "oracle-ring".equals(card.getOracleId()))
                .findFirst().orElseThrow();
        assertEquals("Sol Ring", ring.getName());
        assertTrue(ring.isLegal("commander"));
        assertNotNull(ring.getImageUrl());
        assertTrue(cardRepository.findOnePerOracleId(List.of()).isEmpty());
    }
}
