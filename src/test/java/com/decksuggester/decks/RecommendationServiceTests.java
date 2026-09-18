package com.decksuggester.decks;

import com.decksuggester.spellbook.Combo;
import com.decksuggester.spellbook.ComboRepository;
import com.decksuggester.spellbook.RecommendationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    private final ComboRepository comboRepository = mock(ComboRepository.class);
    private final RecommendationService service = new RecommendationService(comboRepository);

    private Combo combo(String id, String description, List<String> colorIdentity,
                        Combo.ComboCard... cards) throws Exception {
        Constructor<Combo> ctor = Combo.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Combo combo = ctor.newInstance();
        set(combo, "id", id);
        set(combo, "description", description);
        set(combo, "cards", List.of(cards));
        set(combo, "results", List.of(description));
        set(combo, "colorIdentity", colorIdentity);
        return combo;
    }

    private void set(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Combo.ComboCard card(String name, String oracleId, double mana, String type,
                                 List<String> identity, Double price) {
        return new Combo.ComboCard(name + "-sid", oracleId, name, mana, identity, type,
                null, price);
    }

    @Test
    void prefersCombosThatShareMorePiecesAndCheaperNonCreatureCards() throws Exception {
        DeckCard owned = new DeckCard("s1", "oracle-1", "Owned Card", 2.0, List.of("Blue"),
                List.of("Blue"), "Instant", null, 1, List.of(), true, true);
        Deck deck = new Deck(1L, "Deck", null, "url", 1L, "root", 1L, "folder",
                java.time.Instant.now(), List.of(owned));

        // Combo A: deck already owns one of two cheap non-creature pieces.
        Combo comboA = combo("A", "Infinite mana", List.of("Blue"),
                card("Owned Card", "oracle-1", 2.0, "Instant", List.of("Blue"), 1.0),
                card("Cheap Artifact", "oracle-2", 1.0, "Artifact", List.of("Blue"), 1.0));
        // Combo B: deck owns nothing, expensive creatures, off-identity.
        Combo comboB = combo("B", "Infinite damage", List.of("Red"),
                card("Big Creature", "oracle-3", 6.0, "Creature", List.of("Red"), 40.0),
                card("Another Creature", "oracle-4", 5.0, "Creature", List.of("Red"), 30.0));

        when(comboRepository.findAll()).thenReturn(List.of(comboA, comboB));

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
