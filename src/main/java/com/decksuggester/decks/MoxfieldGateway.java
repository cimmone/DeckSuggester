package com.decksuggester.decks;

import tools.jackson.databind.JsonNode;

/** Gateway to the Moxfield public deck API. */
public interface MoxfieldGateway {

    JsonNode fetchDeck(String publicId);
}
