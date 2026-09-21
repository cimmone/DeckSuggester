package com.decksuggester.decks;

/** Identifies the origin of a deck. */
public enum DeckSource {

    /** Imported from an Archidekt folder URL. */
    ARCHIDEKT,
    /** Imported from a single Moxfield deck URL. */
    MOXFIELD,
    /** Created locally by pasting a raw card list. */
    MANUAL
}
