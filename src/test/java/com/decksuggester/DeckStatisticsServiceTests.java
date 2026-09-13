package com.decksuggester;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeckStatisticsServiceTests {

    @Test
    void calculatesQuantityAwareStatisticsAndSkipsMaybeboard() {
        DeckCard redCreatures = new DeckCard("one", "oracle-one", "Red creature", 5,
                List.of("Red"), List.of("Red"), "Legendary Creature — Wizard",
                null, 2, List.of("Main"), true, true);
        DeckCard colorlessArtifact = new DeckCard("two", "oracle-two", "Rock", 2,
                List.of(), List.of(), "Artifact", null, 1,
                List.of("Main"), true, true);
        DeckCard maybeboard = new DeckCard("three", "oracle-three", "Maybe", 9,
                List.of("Blue"), List.of("Blue"), "Instant", null, 10,
                List.of("Maybeboard"), false, true);
        Deck deck = new Deck(1, "Test", "", "https://archidekt.com/decks/1/test",
                10, "Root", 10, "Root", Instant.EPOCH,
                List.of(redCreatures, colorlessArtifact, maybeboard));

        DeckStatisticsService.DataStatistics statistics =
                DeckStatisticsService.calculate(List.of(deck));

        assertThat(statistics.totalDecks()).isEqualTo(1);
        assertThat(statistics.totalCards()).isEqualTo(3);
        assertThat(statistics.manaValues()).containsEntry("5", 2L)
                .containsEntry("2", 1L).doesNotContainKey("9");
        assertThat(statistics.cardColors()).containsEntry("Red", 2L)
                .containsEntry("Colorless", 1L).doesNotContainKey("Blue");
        assertThat(statistics.deckColors()).containsEntry("Red", 1L)
                .containsEntry("Colorless", 1L);
        assertThat(statistics.cardTypes()).containsEntry("Creature", 2L)
                .containsEntry("Artifact", 1L);
    }
}
