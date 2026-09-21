package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import com.decksuggester.cards.CardRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckImportServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArchidektGateway archidekt = mock(ArchidektGateway.class);
    private final MoxfieldGateway moxfield = mock(MoxfieldGateway.class);
    private final CardRepository cardRepository = mock(CardRepository.class);
    private final DeckRepository deckRepository = mock(DeckRepository.class);
    private final DeckHistoryService historyService = mock(DeckHistoryService.class);
    private final DeckImportService service = new DeckImportService(
            archidekt, moxfield, cardRepository, deckRepository, historyService);

    @Test
    void recursivelyImportsFoldersAndMapsDeckCards() throws Exception {
        when(archidekt.fetchFolder(95630)).thenReturn(objectMapper.readTree("""
                {"pageProps":{"redux":{"folders":{"rootFolder":{
                  "id":95630,"name":"Home",
                  "subfolders":[{"id":757175,"name":"Built"}],
                  "decks":[{"id":26251734,"name":"Abilities Tribal"}]
                }}}}}
                """));
        when(archidekt.fetchFolder(757175)).thenReturn(objectMapper.readTree("""
                {"pageProps":{"redux":{"folders":{"rootFolder":{
                  "id":757175,"name":"Built","subfolders":[],
                  "decks":[{"id":99,"name":"Second Deck"}]
                }}}}}
                """));
        when(archidekt.fetchDeck(anyLong(), any(String.class))).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            String name = invocation.getArgument(1);
            return objectMapper.readTree("""
                    {"pageProps":{"redux":{"deck":{
                      "id":%d,"name":"%s","description":"test",
                      "categories":{"Main":{"includedInDeck":true}},
                      "cardMap":{"card-1":{"uid":"printing-id","oracleCardUid":"oracle-id",
                        "name":"Test Card","cmc":5,"colors":["R"],
                        "colorIdentity":["Red"],"types":["Creature"],
                        "subTypes":["Wizard"],"qty":2,"categories":["Main"]}}
                    }}}}
                    """.formatted(id, name));
        });
        when(cardRepository.findByScryfallIdIn(any())).thenReturn(List.of());
        when(cardRepository.findByOracleIdIn(any())).thenReturn(List.of());

        UserIdentity owner = new UserIdentity("alice-id", "alice", "alice-library");
        DeckImportService.ImportResult result = service.importArchidektFolder(
                "https://archidekt.com/folders/95630", owner);

        assertThat(result.foldersVisited()).isEqualTo(2);
        assertThat(result.decksImported()).isEqualTo(2);
        assertThat(result.cardsImported()).isEqualTo(4);
        assertThat(result.unmatchedCards()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Deck>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(deckRepository).saveAll(captor.capture());
        List<Deck> saved = ((List<Deck>) captor.getValue());
        assertThat(saved).extracting(Deck::getRootFolderId).containsOnly(95630L);
        assertThat(saved).extracting(Deck::getRootFolderName).containsOnly("Home");
        assertThat(saved).extracting(Deck::getOwnerId).containsOnly("alice-id");
        assertThat(saved).extracting(Deck::getId).containsExactlyInAnyOrder(
                Deck.deckId("alice-id", 26251734L), Deck.deckId("alice-id", 99L));
        DeckCard savedCard = saved.getFirst().getCards().getFirst();
        assertThat(savedCard.manaValue()).isEqualTo(5);
        assertThat(savedCard.colors()).contains("Red");
        assertThat(savedCard.typeLine()).isEqualTo("Creature — Wizard");
    }

    @Test
    void rejectsNonArchidektUrls() {
        assertThatThrownBy(() -> DeckImportService.extractFolderId(
                "https://example.com/folders/95630"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void importingAMoxfieldDeckCapturesCommanderAsSignatureCards() throws Exception {
        when(moxfield.fetchDeck("A63Mp5u1_UqaBpoMmJDXYg")).thenReturn(objectMapper.readTree("""
                {"name":"Dwalin","description":"hobbit","publicUrl":"https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg","publicId":"A63Mp5u1_UqaBpoMmJDXYg",
                 "boards":{"commanders":{"count":1,"cards":{"kX8zV":{"quantity":1,"boardType":"commanders",
                   "card":{"scryfall_id":"cmd-1","name":"Dwalin, Weaponmaster","cmc":2,
                     "type_line":"Legendary Creature \u2014 Dwarf Warrior","colors":["R","W"],
                     "color_identity":["R","W"],"power":"2","toughness":"1","released_at":"2026-08-14","layout":"normal"}}}},
                   "mainboard":{"count":1,"cards":{"abc":{"quantity":1,"boardType":"mainboard",
                   "card":{"scryfall_id":"main-1","name":"Mountain","cmc":0,"type_line":"Basic Land \u2014 Mountain",
                     "colors":[],"color_identity":["R"],"layout":"normal"}}}},
                   "maybeboard":{"count":0,"cards":{}}}}
                """));
        when(cardRepository.findByScryfallIdIn(any())).thenReturn(List.of());
        when(cardRepository.findByOracleIdIn(any())).thenReturn(List.of());
        when(deckRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        UserIdentity owner = new UserIdentity("alice-id", "alice", "alice-library");
        DeckImportService.ImportResult result = service.importByUrl(
                "https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg", owner);

        assertThat(result.decksImported()).isEqualTo(1);
        org.mockito.ArgumentCaptor<Deck> captor = org.mockito.ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(captor.capture());
        Deck saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(DeckSource.MOXFIELD);
        assertThat(saved.getSignatureCards()).hasSize(1);
        assertThat(saved.getSignatureCards().get(0).name()).isEqualTo("Dwalin, Weaponmaster");
        assertThat(saved.getSignatureCards().get(0).categories()).contains("Commander");
        assertThat(saved.getCards()).hasSize(1);
        assertThat(saved.getCards().get(0).name()).isEqualTo("Mountain");
        assertThat(saved.getColorIdentity()).containsExactlyInAnyOrder("Red", "White");
        assertThat(saved.getSourceUrl()).isEqualTo("https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg");
    }

    @Test
    void importingAManualDecklistRequiresASignatureCardAndSavesItSeparately() {
        when(cardRepository.findFirstByNameIgnoreCase("Rhys the Redeemed"))
                .thenReturn(java.util.Optional.of(scryfallCard("rhys-1", "Rhys the Redeemed",
                        "Legendary Creature \u2014 Elf Warrior", List.of("G"), "1", "1", "2007-10-01")));
        // The decklist's card names are resolved via a single batched lookup;
        // "Forest" is a known Scryfall card in this mock and "Sol Ring" is not,
        // so the batch only returns the one match.
        when(cardRepository.findByNameInIgnoreCase(any())).thenReturn(List.of(
                scryfallCard("forest-1", "Forest", "Basic Land \u2014 Forest",
                        List.of(), null, null, "1993-08-05")));
        when(deckRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        UserIdentity owner = new UserIdentity("alice-id", "alice", "alice-library");
        Deck saved = service.importManualDeck(
                new DeckImportService.ManualImportRequest("My Deck", "", "Rhys the Redeemed",
                        "1 Sol Ring\n2 Forest", null),
                owner);

        assertThat(saved.getSource()).isEqualTo(DeckSource.MANUAL);
        assertThat(saved.getName()).isEqualTo("My Deck");
        assertThat(saved.getSignatureCards()).hasSize(1);
        assertThat(saved.getSignatureCards().get(0).name()).isEqualTo("Rhys the Redeemed");
        assertThat(saved.getCards()).hasSize(2);
        // Sol Ring is not a known Scryfall card in this mock, so it is kept as
        // an unmatched placeholder with its parsed quantity.
        assertThat(saved.getCards().get(0).name()).isEqualTo("Sol Ring");
        assertThat(saved.getCards().get(0).quantity()).isEqualTo(1);
        assertThat(saved.getCards().get(0).matchedScryfallCard()).isFalse();
        // Forest resolves via the batched lookup and is fully hydrated.
        assertThat(saved.getCards().get(1).name()).isEqualTo("Forest");
        assertThat(saved.getCards().get(1).quantity()).isEqualTo(2);
        assertThat(saved.getCards().get(1).matchedScryfallCard()).isTrue();
        assertThat(saved.getCards().get(1).scryfallId()).isEqualTo("forest-1");
    }

    private com.decksuggester.cards.Card scryfallCard(String id, String name, String typeLine,
                                                       List<String> identity, String power,
                                                       String toughness, String releasedAt) {
        com.decksuggester.cards.Card card = new com.decksuggester.cards.Card();
        setField(card, "scryfallId", id);
        setField(card, "name", name);
        setField(card, "typeLine", typeLine);
        setField(card, "colorIdentity", identity);
        setField(card, "colors", identity);
        setField(card, "power", power);
        setField(card, "toughness", toughness);
        setField(card, "releasedAt", releasedAt);
        return card;
    }

    private static void setField(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
