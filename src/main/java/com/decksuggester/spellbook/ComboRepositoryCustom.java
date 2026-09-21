package com.decksuggester.spellbook;

import java.util.Collection;
import java.util.List;

/** Hand-written half of {@link ComboRepository}, needed because the per-format legality check uses a dynamic field name that a static {@code @Aggregation} string can't express. */
public interface ComboRepositoryCustom {

    /** Combo candidates matching the identity regex and (if tracked) format legality, capped at {@code limit}. */
    List<Combo> findCandidates(String identityRegex, String format, int limit);

    /** Every combo (no cap) matching the same filters as {@link #findCandidates}, further restricted to combos using at least one of the given oracle ids. */
    List<Combo> findCandidatesForOwnedCards(String identityRegex, String format,
                                            Collection<String> ownedOracleIds);
}
