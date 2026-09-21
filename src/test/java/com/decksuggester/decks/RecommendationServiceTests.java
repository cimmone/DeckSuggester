package com.decksuggester.decks;

import com.decksuggester.cards.Card;
import com.decksuggester.cards.CardRepository;
import com.decksuggester.spellbook.Combo;
import com.decksuggester.spellbook.ComboRepository;
import com.decksuggester.spellbook.RecommendationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    private final ComboRepository comboRepository = mock(ComboRepository.class);
    private final CardRepository cardRepository = mock(CardRepository.class);
    private final RecommendationService service =
            new RecommendationService(comboRepository, cardRepository);

    private Combo combo(String id, String description, String identity, String tcgPrice,
                        Combo.Use... uses) throws Exception {
        Combo combo = new Combo();
        set(combo, "id", id);
        set(combo, "description", description);
        set(combo, "identity", identity);
        set(combo, "uses", List.of(uses));
        set(combo, "produces", List.of(new Combo.Produce(new Combo.Feature(description), 1)));
        set(combo, "prices", new Combo.Prices(tcgPrice, null, null));
        set(combo, "popularity", 10);
        set(combo, "legalities", new Combo.ComboLegalities(true, true, true, true, false, true));
        return combo;
    }

    private void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Combo.Use use(String name, String oracleId) {
        // The Scryfall id is derived from the image URL's filename.
        String imageUrl = "https://cards.scryfall.io/normal/front/a/b/" + name.replace(' ', '-')
                + ".jpg";
        return new Combo.Use(new Combo.Card(1L, name, oracleId, null, imageUrl), 1);
    }

    private Card scryfall(String oracleId, double cmc, String typeLine, List<String> identity) {
        Card card = new Card();
        setCard(card, "oracleId", oracleId);
        setCard(card, "cmc", cmc);
        setCard(card, "typeLine", typeLine);
        setCard(card, "colorIdentity", identity);
        setCard(card, "legalities", Map.of("commander", "legal"));
        return card;
    }

    private void setCard(Card card, String field, Object value) {
        try {
            Field f = Card.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(card, value);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void prefersCombosThatShareMorePiecesAndCheaperNonCreatureCards() throws Exception {
        DeckCard owned = new DeckCard("s1", "oracle-1", "Owned Card", 2.0, List.of("Blue"),
                List.of("Blue"), "Instant", null, 1, List.of(), true, true, null, null, null, null, null);
        Deck deck = new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                java.time.Instant.now(), List.of(owned));

        // Combo A: deck already owns one of two cheap non-creature pieces.
        Combo comboA = combo("A", "Infinite mana", "U", "2.00",
                use("Owned Card", "oracle-1"), use("Cheap Artifact", "oracle-2"));
        // Combo B: deck owns nothing, expensive creatures (but on-identity so the
        // colour-identity filter does not drop it before scoring).
        Combo comboB = combo("B", "Infinite damage", "U", "70.00",
                use("Big Creature", "oracle-3"), use("Another Creature", "oracle-4"));

        // Mongo-side candidate query is mocked to return the combos in the
        // *opposite* of their expected fitness order (B is a much worse fit
        // than A). The service must re-sort by fitness, not trust Mongo's
        // candidate order.
        when(comboRepository.findCandidates(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(comboB, comboA));
        List<Card> cards = new ArrayList<>(List.of(
                scryfall("oracle-1", 2.0, "Instant", List.of("U")),
                scryfall("oracle-2", 1.0, "Artifact", List.of("U")),
                scryfall("oracle-3", 6.0, "Creature", List.of("U")),
                scryfall("oracle-4", 5.0, "Creature", List.of("U"))));
        when(cardRepository.findByOracleIdIn(anyCollection())).thenReturn(cards);

        List<RecommendationService.Recommendation> recommendations = service.recommend(deck, 50);

        assertThat(recommendations).hasSize(2);
        assertThat(recommendations.get(0).comboId()).isEqualTo("A");
        assertThat(recommendations.get(0).fitness())
                .isLessThan(recommendations.get(1).fitness());
        assertThat(recommendations.get(0).ownedPieces()).isEqualTo(1);
        assertThat(recommendations.get(0).newCards()).hasSize(1);
        assertThat(recommendations.get(0).newCards().get(0).name()).isEqualTo("Cheap Artifact");
        assertThat(recommendations.get(0).explanation()).contains("colour identity");
    }

    @Test
    void colorIdentityRegexMatchesOnlySubsetIdentities() {
        assertThat(RecommendationService.identityRegex(java.util.Set.of("U")))
                .isEqualTo("^([U]*|C)$");
        assertThat(RecommendationService.identityRegex(java.util.Set.of("W", "R")))
                .isEqualTo("^([RW]*|C)$");
        // A colourless commander only allows colourless combos, which Commander
        // Spellbook stores with the literal identity "C".
        assertThat(RecommendationService.identityRegex(java.util.Set.of())).isEqualTo("^C$");
    }

    @Test
    void colorIdentityRegexAlwaysAcceptsColorlessCombos() {
        assertThat("U").matches(RecommendationService.identityRegex(java.util.Set.of("U")));
        assertThat("C").matches(RecommendationService.identityRegex(java.util.Set.of("U")));
        assertThat("W").doesNotMatch(RecommendationService.identityRegex(java.util.Set.of("U")));
        assertThat("C").matches(RecommendationService.identityRegex(java.util.Set.of()));
        assertThat("U").doesNotMatch(RecommendationService.identityRegex(java.util.Set.of()));
    }

    @Test
    void ownedFilterReturnsOnlyFullyAssembledCombos() throws Exception {
        DeckCard owned = new DeckCard("s1", "oracle-1", "Owned Card", 2.0, List.of("Blue"),
                List.of("Blue"), "Instant", null, 1, List.of(), true, true, null, null, null, null, null);
        Deck deck = new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                java.time.Instant.now(), List.of(owned));

        Combo comboA = combo("A", "Infinite mana", "U", "2.00",
                use("Owned Card", "oracle-1"), use("Cheap Artifact", "oracle-2"));
        Combo comboOwned = combo("C", "Assembled", "U", "1.00",
                use("Owned Card", "oracle-1"));
        // OWNED/PARTIAL modes query an uncapped, owned-card-restricted
        // candidate set instead of the popularity-agnostic capped one.
        when(comboRepository.findCandidatesForOwnedCards(anyString(), anyString(), anyCollection()))
                .thenReturn(List.of(comboA, comboOwned));
        when(cardRepository.findByOracleIdIn(anyCollection())).thenReturn(List.of(
                scryfall("oracle-1", 2.0, "Instant", List.of("U")),
                scryfall("oracle-2", 1.0, "Artifact", List.of("U"))));

        List<RecommendationService.Recommendation> ownedOnly = service.recommend(deck, 50,
                RecommendationService.FilterMode.OWNED, "commander");

        assertThat(ownedOnly).hasSize(1);
        assertThat(ownedOnly.get(0).comboId()).isEqualTo("C");
        assertThat(ownedOnly.get(0).ownedPieces())
                .isEqualTo(ownedOnly.get(0).requiredPieces());
        // The capped, popularity-agnostic candidate query must not be used for
        // ownership-based filtering, since a combo the deck fully owns could
        // otherwise be truncated away before ownership is ever checked.
        verify(comboRepository, never()).findCandidates(anyString(), anyString(), anyInt());
    }

    @Test
    void partialFilterIsNotLimitedByTheGeneralCandidateCap() throws Exception {
        DeckCard owned = new DeckCard("s1", "oracle-1", "Owned Card", 2.0, List.of("Blue"),
                List.of("Blue"), "Instant", null, 1, List.of(), true, true, null, null, null, null, null);
        Deck deck = new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                java.time.Instant.now(), List.of(owned));

        Combo comboPartial = combo("A", "Infinite mana", "U", "2.00",
                use("Owned Card", "oracle-1"), use("Cheap Artifact", "oracle-2"));
        when(comboRepository.findCandidatesForOwnedCards(anyString(), anyString(), anyCollection()))
                .thenReturn(List.of(comboPartial));
        when(cardRepository.findByOracleIdIn(anyCollection())).thenReturn(List.of(
                scryfall("oracle-1", 2.0, "Instant", List.of("U")),
                scryfall("oracle-2", 1.0, "Artifact", List.of("U"))));

        List<RecommendationService.Recommendation> partial = service.recommend(deck, 50,
                RecommendationService.FilterMode.PARTIAL, "commander");

        assertThat(partial).hasSize(1);
        assertThat(partial.get(0).comboId()).isEqualTo("A");
        verify(comboRepository, never()).findCandidates(anyString(), anyString(), anyInt());
    }
}
