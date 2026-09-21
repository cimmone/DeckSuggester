package com.decksuggester.decks;

import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.CardRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the Moxfield importer against the curated
 * {@code src/test/resources/moxfield/deck.json} fixture so the parsing of
 * Moxfield's board shape (commanders / signatureSpells / mainboard /
 * maybeboard / sideboard) stays correct as the schema evolves.
 */
class MoxfieldFixtureImportTests {

    private static final String FIXTURE_DIRECTORY =
            System.getProperty("user.dir") + "/src/test/resources/moxfield";

    @Test
    void parsesTheCapturedMoxfieldDeckFixture() {
        MoxfieldClient moxfieldClient = new MoxfieldClient(new ObjectMapper(),
                java.net.http.HttpClient.newHttpClient(),
                "https://api2.moxfield.com", FIXTURE_DIRECTORY);
        CardRepository cardRepository = mock(CardRepository.class);
        DeckRepository deckRepository = mock(DeckRepository.class);
        DeckHistoryService historyService = mock(DeckHistoryService.class);
        DeckImportService service = new DeckImportService(
                mock(ArchidektGateway.class), moxfieldClient, cardRepository,
                deckRepository, historyService);
        when(cardRepository.findByScryfallIdIn(any())).thenReturn(List.of());
        when(cardRepository.findByOracleIdIn(any())).thenReturn(List.of());
        when(deckRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserIdentity owner = new UserIdentity("alice", "alice", "alice-library");
        DeckImportService.ImportResult result = service.importByUrl(
                "https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg", owner);

        assertThat(result.decksImported()).isEqualTo(1);
        ArgumentCaptor<Deck> captor = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(captor.capture());
        Deck deck = captor.getValue();
        assertThat(deck.getSource()).isEqualTo(DeckSource.MOXFIELD);
        assertThat(deck.getName()).isEqualTo("Dwalin");
        assertThat(deck.getSourceUrl())
                .isEqualTo("https://moxfield.com/decks/dZySsdmucEqqBUfQpDn_ag");
        // One commander in the fixture, captured as a signature card.
        assertThat(deck.getSignatureCards()).hasSize(1);
        assertThat(deck.getSignatureCards().get(0).name())
                .isEqualTo("Dwalin, Weaponmaster");
        assertThat(deck.getSignatureCards().get(0).categories()).contains("Commander");
        // Mainboard + maybeboard + sideboard entries all flow into the card list
        // (mainboard included, the other two flagged not-included).
        assertThat(deck.getCards()).hasSize(3 + 2 + 1);
        long included = deck.getCards().stream()
                .filter(com.decksuggester.decks.DeckCard::includedInDeck).count();
        assertThat(included).isEqualTo(3);
        // The commander drives the deck colour identity.
        assertThat(deck.getColorIdentity()).containsExactlyInAnyOrder("Red", "White");
    }
}
