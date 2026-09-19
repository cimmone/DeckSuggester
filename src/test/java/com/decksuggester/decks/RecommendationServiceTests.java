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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
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
                List.of("Blue"), "Instant", null, 1, List.of(), true, true);
        Deck deck = new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                java.time.Instant.now(), List.of(owned));

        // Combo A: deck already owns one of two cheap non-creature pieces.
        Combo comboA = combo("A", "Infinite mana", "U", "2.00",
                use("Owned Card", "oracle-1"), use("Cheap Artifact", "oracle-2"));
        // Combo B: deck owns nothing, expensive creatures, off-identity.
        Combo comboB = combo("B", "Infinite damage", "R", "70.00",
                use("Big Creature", "oracle-3"), use("Another Creature", "oracle-4"));

        when(comboRepository.findAll()).thenReturn(List.of(comboA, comboB));
        List<Card> cards = new ArrayList<>(List.of(
                scryfall("oracle-1", 2.0, "Instant", List.of("U")),
                scryfall("oracle-2", 1.0, "Artifact", List.of("U")),
                scryfall("oracle-3", 6.0, "Creature", List.of("R")),
                scryfall("oracle-4", 5.0, "Creature", List.of("R"))));
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
}
