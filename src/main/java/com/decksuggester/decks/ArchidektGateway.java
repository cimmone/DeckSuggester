package com.decksuggester.decks;

import tools.jackson.databind.JsonNode;

public interface ArchidektGateway {

    JsonNode fetchFolder(long folderId);

    JsonNode fetchDeck(long deckId, String deckName);
}
