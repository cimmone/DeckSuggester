package com.decksuggester.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {

    Optional<UserAccount> findByUsernameKey(String usernameKey);

    Optional<UserAccount> findByEmailKey(String emailKey);

    Optional<UserAccount> findByPasswordResetTokenHash(String passwordResetTokenHash);

    boolean existsByUsernameKey(String usernameKey);

    boolean existsByEmailKey(String emailKey);

    boolean existsByLibraryId(String libraryId);
}
