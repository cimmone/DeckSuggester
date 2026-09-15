package com.decksuggester.migrations;

import com.mongodb.client.model.IndexOptions;
import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Change(id = "V001-user-lookup-indexes", author = "deck-suggester", transactional = false)
@TargetSystem(id = "mongodb")
@Recovery(strategy = RecoveryStrategy.ALWAYS_RETRY)
public class _0001__UserIndexes {

    @Apply
    public void apply(MongoTemplate mongo) {
        var users = mongo.getCollection("users");
        users.createIndex(new Document("usernameKey", 1), onlineUnique("users_username_key"));
        users.createIndex(new Document("emailKey", 1), onlineUnique("users_email_key"));
        users.createIndex(new Document("libraryId", 1), onlineUnique("users_library_id"));
        users.createIndex(new Document("passwordResetTokenHash", 1),
                new IndexOptions().name("users_password_reset_token").background(true)
                        .sparse(true));
    }

    private static IndexOptions onlineUnique(String name) {
        return new IndexOptions().name(name).unique(true).background(true);
    }
}
