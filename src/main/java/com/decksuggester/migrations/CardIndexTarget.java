package com.decksuggester.migrations;

import org.springframework.data.mongodb.core.MongoTemplate;

/** Wrapper handing a change the scryfall {@link MongoTemplate}, mirroring {@link SpellbookIndexTarget}. */
public record CardIndexTarget(MongoTemplate cardsTemplate) {
}
