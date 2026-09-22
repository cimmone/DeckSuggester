package com.decksuggester.decks.external;

import tools.jackson.databind.JsonNode;

/** Gateway to the Archidekt public folder/deck API. */
public interface ArchidektGateway {

    JsonNode fetchFolder(long folderId);

    JsonNode fetchDeck(long deckId, String deckName);
}
