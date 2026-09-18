package com.decksuggester.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class UserAccountService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LIBRARY_ID_BYTES = 9;

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String resetTokenPepper;
    private final Duration resetLifetime;
    private final String publicBaseUrl;

    public UserAccountService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                              @Value("${auth.password-salt}") String resetTokenPepper,
                              @Value("${auth.password-reset-minutes:30}") long resetMinutes,
                              @Value("${app.public-base-url:http://localhost:8080}")
                              String publicBaseUrl) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenPepper = resetTokenPepper;
        this.resetLifetime = Duration.ofMinutes(Math.max(5, resetMinutes));
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsernameKey(key(username))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }

    /**
     * Registers a new account. The library id is generated here rather than
     * collected from the user: it only needs to be unique, not memorable or
     * chosen, so asking for it at signup would just be friction.
     */
    public UserAccount register(String username, String password, String email) {
        String usernameKey = key(username);
        String emailKey = key(email);
        if (repository.existsByUsernameKey(usernameKey)) {
            throw new AccountConflictException("That username is already in use");
        }
        if (repository.existsByEmailKey(emailKey)) {
            throw new AccountConflictException("That email is already in use");
        }
        UserAccount account = new UserAccount(username.trim(), usernameKey, email.trim(),
                emailKey, passwordEncoder.encode(password), allocateLibraryId(), Instant.now());
        try {
            return repository.save(account);
        } catch (DuplicateKeyException exception) {
            throw new AccountConflictException("Username or email is already in use");
        }
    }

    public void requestPasswordReset(String usernameOrEmail) {
        String accountKey = key(usernameOrEmail);
        repository.findByUsernameKey(accountKey)
                .or(() -> repository.findByEmailKey(accountKey))
                .ifPresent(account -> {
                    byte[] bytes = new byte[32];
                    SECURE_RANDOM.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    account.setPasswordReset(tokenHash(token), Instant.now().plus(resetLifetime));
                    repository.save(account);
                    log.warn("Password reset requested for '{}'. Reset URL: {}/ui/?resetToken={}",
                            account.getUsername(), publicBaseUrl, token);
                });
    }

    public void resetPassword(String token, String password) {
        UserAccount account = repository.findByPasswordResetTokenHash(tokenHash(token))
                .filter(candidate -> candidate.getPasswordResetExpiresAt() != null
                        && candidate.getPasswordResetExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new InvalidResetTokenException(
                        "That password reset link is invalid or has expired"));
        account.setPasswordHash(passwordEncoder.encode(password));
        account.clearPasswordReset();
        repository.save(account);
    }

    private String allocateLibraryId() {
        String libraryId;
        do {
            byte[] bytes = new byte[LIBRARY_ID_BYTES];
            SECURE_RANDOM.nextBytes(bytes);
            libraryId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                    .toLowerCase(Locale.ROOT);
        } while (repository.existsByLibraryId(libraryId));
        return libraryId;
    }

    private String tokenHash(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidResetTokenException("That password reset link is invalid or has expired");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (resetTokenPepper + ":" + token).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
