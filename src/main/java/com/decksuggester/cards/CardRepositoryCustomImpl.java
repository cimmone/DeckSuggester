package com.decksuggester.cards;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
class CardRepositoryCustomImpl implements CardRepositoryCustom {

    // Everything Card#... and DeckImportService#deckCardFromCard read; skips oracle text, rulings, prices and other large payloads.
    private static final Document PROJECTION = new Document()
            .append("oracle_id", 1).append("id", 1).append("name", 1).append("cmc", 1)
            .append("colors", 1).append("color_identity", 1).append("type_line", 1)
            .append("power", 1).append("toughness", 1).append("released_at", 1)
            .append("layout", 1).append("legalities", 1).append("image_uris", 1)
            .append("card_faces.image_uris", 1);

    private final MongoTemplate scryfallMongoTemplate;

    CardRepositoryCustomImpl(@Qualifier("scryfallMongoTemplate") MongoTemplate scryfallMongoTemplate) {
        this.scryfallMongoTemplate = scryfallMongoTemplate;
    }

    @Override
    public List<Card> findOnePerOracleId(Collection<String> oracleIds) {
        if (oracleIds.isEmpty()) {
            return List.of();
        }
        List<AggregationOperation> stages = List.of(
                context -> new Document("$match",
                        new Document("oracle_id", new Document("$in", List.copyOf(oracleIds)))),
                context -> new Document("$project", PROJECTION),
                context -> new Document("$group", new Document("_id", "$oracle_id")
                        .append("card", new Document("$first", "$$ROOT"))),
                context -> new Document("$replaceRoot", new Document("newRoot", "$card")));
        return scryfallMongoTemplate
                .aggregate(Aggregation.newAggregation(stages), "cards", Card.class)
                .getMappedResults();
    }
}
