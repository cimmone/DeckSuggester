package com.decksuggester.spellbook;

import com.decksuggester.cards.CardRepository;
import com.decksuggester.decks.Deck;
import com.decksuggester.decks.DeckCard;
import com.decksuggester.decks.DeckSource;
import com.decksuggester.testsupport.EmbeddedMongoSupport;
import com.decksuggester.testsupport.TestCards;
import com.decksuggester.testsupport.TestCombos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the optimised recommendation pipeline against a real (embedded)
 * Mongo instance seeded with a handful of hand-built combos: the
 * colour-identity and combo-level format-legality filters run on the Mongo
 * side to pick the candidate set, and the final list is sorted by the
 * per-deck fitness score computed over that candidate set.
 */
@SpringBootTest(properties = "app.migrations.enabled=false")
class RecommendationServiceEndToEndTests {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb.host", EmbeddedMongoSupport::connectionString);
    }

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private ComboRepository comboRepository;

    @Autowired
    private CardRepository cardRepository;

    @Test
    void returnsOnlyOnIdentityCombosSortedByFitness() {
        Map<String, String> commanderLegal = Map.of("commander", "legal");
        cardRepository.saveAll(List.of(
                TestCards.card("piece-blue-1", "piece-blue-1", "Blue Piece One", 2.0,
                        "Instant", List.of("U"), List.of("U"), null, null, "2020-01-01",
                        commanderLegal, null),
                TestCards.card("piece-blue-2", "piece-blue-2", "Blue Piece Two", 1.0,
                        "Artifact", List.of(), List.of("U"), null, null, "2020-01-01",
                        commanderLegal, null),
                TestCards.card("piece-colorless-1", "piece-colorless-1", "Colourless Piece",
                        0.0, "Artifact", List.of(), List.of(), null, null, "2020-01-01",
                        commanderLegal, null),
                TestCards.card("piece-red-1", "piece-red-1", "Red Piece", 1.0, "Sorcery",
                        List.of("R"), List.of("R"), null, null, "2020-01-01",
                        commanderLegal, null)));

        // On-identity: matches a mono-Blue commander.
        Combo comboBlue = TestCombos.combo("U", true,
                TestCombos.use("Blue Piece One", "piece-blue-1", "Instant"),
                TestCombos.use("Blue Piece Two", "piece-blue-2", "Artifact"));
        // Colourless combos are stored with the literal identity "C" and must
        // remain eligible for every commander regardless of colour.
        Combo comboColorless = TestCombos.combo("C", true,
                TestCombos.use("Colourless Piece", "piece-colorless-1", "Artifact"));
        // Off-identity: a mono-Red combo must never be recommended to a
        // mono-Blue commander.
        Combo comboRed = TestCombos.combo("R", true,
                TestCombos.use("Red Piece", "piece-red-1", "Sorcery"));
        comboRepository.saveAll(List.of(comboBlue, comboColorless, comboRed));

        DeckCard commander = new DeckCard("cmd", "oracle-cmd", "Commander", 4.0,
                List.of("Blue"), List.of("Blue"), "Legendary Creature", null, 1,
                List.of("Commander"), true, true, null, null, null, null, null);
        Deck deck = new Deck(new com.decksuggester.auth.UserIdentity("rec-owner", "rec-owner",
                "rec-library"), 1L, "Blue Deck", null, null, 0L, "root", 0L, "folder",
                Instant.now(), List.of(), DeckSource.MANUAL, List.of(commander), null);

        List<RecommendationService.Recommendation> recs = recommendationService.recommend(
                deck, 50, RecommendationService.FilterMode.LEGAL, "commander");

        assertThat(recs).extracting(RecommendationService.Recommendation::comboId)
                .containsExactlyInAnyOrder(comboBlue.getId(), comboColorless.getId());
        assertThat(recs).isSortedAccordingTo(
                Comparator.comparingDouble(RecommendationService.Recommendation::fitness));
    }
}
