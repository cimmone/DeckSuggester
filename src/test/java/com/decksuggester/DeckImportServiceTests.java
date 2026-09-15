package com.decksuggester;

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
    private final CardRepository cardRepository = mock(CardRepository.class);
    private final DeckRepository deckRepository = mock(DeckRepository.class);
    private final DeckImportService service = new DeckImportService(
            archidekt, cardRepository, deckRepository);

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
        DeckImportService.ImportResult result = service.importFolder(
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
}
