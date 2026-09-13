package com.decksuggester;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(@Value("${spring.mongodb.uri}") String uri) {
        ConnectionString connectionString = new ConnectionString(uri);
        MongoClient mongoClient = MongoClients.create(connectionString);
        return new SimpleMongoClientDatabaseFactory(mongoClient, connectionString.getDatabase());
    }

}
