package com.decksuggester.decks;

import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.CardRepository;
import com.decksuggester.testsupport.EmbeddedMongoSupport;
import com.decksuggester.testsupport.TestCards;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that the Moxfield importer works against the curated
 * {@code src/test/resources/moxfield/deck.json} fixture and the local
 * Scryfall card catalog: the commander is resolved to a real Scryfall card
 * (with an image URL), the deck is tagged as a Moxfield import, and its
 * colour identity is derived from the signature card.
 */
@SpringBootTest(properties = "app.migrations.enabled=false")
class MoxfieldImportEndToEndTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mongodb.host", EmbeddedMongoSupport::connectionString);
        registry.add("moxfield.fixture-directory",
                () -> System.getProperty("user.dir") + "/src/test/resources/moxfield");
    }

    @Autowired
    private DeckImportService importService;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Test
    void importsTheCapturedMoxfieldDeckWithSignatureCardsAndImages() {
        // The commander resolves to a real local Scryfall card so the import
        // picks up its image URL rather than falling back to the raw Moxfield
        // metadata alone.
        cardRepository.save(TestCards.card("cmd-1", "cmd-1-oracle", "Dwalin, Weaponmaster", 2.0,
                "Legendary Creature — Dwarf Warrior", List.of("R", "W"), List.of("R", "W"),
                "2", "1", "2026-08-14", Map.of("commander", "legal"),
                "https://cards.scryfall.io/normal/front/c/m/cmd-1.jpg"));

        UserIdentity owner = new UserIdentity("it-owner", "it-owner", "it-library");
        DeckImportService.ImportResult result = importService.importByUrl(
                "https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg", owner);

        assertThat(result.decksImported()).isEqualTo(1);
        Deck deck = deckRepository.findByIdAndOwnerIdAndLibraryId(
                result.deckNames().isEmpty() ? null : deckIdFor(owner, result), owner.ownerId(),
                owner.libraryId()).orElse(null);
        // Fall back to looking the deck up by source URL if the id helper missed.
        if (deck == null) {
            deck = deckRepository.findAllByOwnerIdAndLibraryId(owner.ownerId(), owner.libraryId())
                    .stream().filter(d -> d.getSource() == DeckSource.MOXFIELD)
                    .findFirst().orElseThrow();
        }
        try {
            assertThat(deck.getSource()).isEqualTo(DeckSource.MOXFIELD);
            assertThat(deck.getSignatureCards()).hasSize(1);
            DeckCard commander = deck.getSignatureCards().get(0);
            assertThat(commander.name()).isEqualTo("Dwalin, Weaponmaster");
            // The commander resolved to a real Scryfall card, so it carries an
            // image URL and matching metadata.
            assertThat(commander.matchedScryfallCard()).isTrue();
            assertThat(commander.imageUrl()).startsWith("https://cards.scryfall.io/");
            assertThat(commander.power()).isEqualTo("2");
            assertThat(commander.toughness()).isEqualTo("1");
            assertThat(commander.releasedAt()).isEqualTo("2026-08-14");
            assertThat(deck.getColorIdentity()).containsExactlyInAnyOrder("Red", "White");
            assertThat(deck.getSourceUrl())
                    .isEqualTo("https://moxfield.com/decks/dZySsdmucEqqBUfQpDn_ag");
        } finally {
            deckRepository.delete(deck);
        }
    }

    private static String deckIdFor(UserIdentity owner, DeckImportService.ImportResult result) {
        // The deterministic deck id is owner + the stable moxfield external id.
        return Deck.deckId(owner.ownerId(),
                DeckImportService.stableLong("moxfield:" + "A63Mp5u1_UqaBpoMmJDXYg"));
    }
}
