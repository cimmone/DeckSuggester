package com.decksuggester.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class UserAccount {

    @Id
    private String id;
    private String username;
    private String usernameKey;
    private String email;
    private String emailKey;
    private String passwordHash;
    private String libraryId;
    private Instant createdAt;
    private String passwordResetTokenHash;
    private Instant passwordResetExpiresAt;

    public UserAccount() {
    }

    public UserAccount(String username, String usernameKey, String email, String emailKey,
                       String passwordHash, String libraryId, Instant createdAt) {
        this.username = username;
        this.usernameKey = usernameKey;
        this.email = email;
        this.emailKey = emailKey;
        this.passwordHash = passwordHash;
        this.libraryId = libraryId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameKey() {
        return usernameKey;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailKey() {
        return emailKey;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getLibraryId() {
        return libraryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPasswordResetTokenHash() {
        return passwordResetTokenHash;
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setPasswordReset(String tokenHash, Instant expiresAt) {
        this.passwordResetTokenHash = tokenHash;
        this.passwordResetExpiresAt = expiresAt;
    }

    public void clearPasswordReset() {
        this.passwordResetTokenHash = null;
        this.passwordResetExpiresAt = null;
    }
}
