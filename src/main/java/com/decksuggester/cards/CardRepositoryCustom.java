package com.decksuggester.cards;

import java.util.Collection;
import java.util.List;

/** Hand-written half of {@link CardRepository}, needed because the lookup groups server-side, which a derived query can't express. */
public interface CardRepositoryCustom {

    /**
     * One card (an arbitrary printing) per oracle id, with only the fields deck
     * import and combo scoring read. Looking up by oracle id otherwise returns
     * every printing of every card, which is many times more documents than needed.
     */
    List<Card> findOnePerOracleId(Collection<String> oracleIds);
}
