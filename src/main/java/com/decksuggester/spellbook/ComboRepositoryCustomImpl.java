package com.decksuggester.spellbook;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
class ComboRepositoryCustomImpl implements ComboRepositoryCustom {

    // Commander Spellbook only reports combo-level legality for these formats
    // (see Combo.ComboLegalities); any other format falls back to the
    // per-card Scryfall legality check in RecommendationService.
    private static final Set<String> KNOWN_LEGALITY_FORMATS = Set.of(
            "commander", "vintage", "legacy", "modern", "pauper", "oathbreaker");

    private final MongoTemplate spellbookMongoTemplate;

    ComboRepositoryCustomImpl(@Qualifier("spellbookMongoTemplate") MongoTemplate spellbookMongoTemplate) {
        this.spellbookMongoTemplate = spellbookMongoTemplate;
    }

    @Override
    public List<Combo> findCandidates(String identityRegex, String format, int limit) {
        // Identity + legality run as $match stages ahead of $limit, on Mongo's side, so the full variants collection is never loaded into memory.
        List<AggregationOperation> stages = identityAndLegalityStages(identityRegex, format);
        stages.add(Aggregation.limit(limit));
        return spellbookMongoTemplate
                .aggregate(Aggregation.newAggregation(stages), "variants", Combo.class)
                .getMappedResults();
    }

    @Override
    public List<Combo> findCandidatesForOwnedCards(String identityRegex, String format,
                                                    Collection<String> ownedOracleIds) {
        if (ownedOracleIds.isEmpty()) {
            return List.of();
        }
        // Unlike findCandidates, this is not capped: it is already narrowed to
        // combos touching at least one card the deck owns, which is a small
        // set regardless of how large the identity-matched pool is, so the
        // "already in deck" / "partially in deck" filters see every eligible
        // combo instead of only whichever ones happened to survive a
        // popularity-agnostic top-N cut.
        List<AggregationOperation> stages = identityAndLegalityStages(identityRegex, format);
        stages.add(Aggregation.match(Criteria.where("uses.card.oracleId").in(ownedOracleIds)));
        return spellbookMongoTemplate
                .aggregate(Aggregation.newAggregation(stages), "variants", Combo.class)
                .getMappedResults();
    }

    private List<AggregationOperation> identityAndLegalityStages(String identityRegex, String format) {
        List<AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(Criteria.where("identity").regex(identityRegex)));

        String legalityFormat = format == null ? null : format.trim().toLowerCase(Locale.ROOT);
        if (legalityFormat != null && KNOWN_LEGALITY_FORMATS.contains(legalityFormat)) {
            stages.add(Aggregation.match(Criteria.where("legalities." + legalityFormat).is(true)));
        }
        return stages;
    }
}
