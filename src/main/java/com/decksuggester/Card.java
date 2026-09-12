package com.decksuggester;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cards")
public class Card {

    @Id
    private String mongoId;

    private String id;
    private String oracleId;
    private String name;

    public String getMongoId() {
        return mongoId;
    }

    public String getId() {
        return id;
    }

    public String getOracleId() {
        return oracleId;
    }

    public String getName() {
        return name;
    }

}
