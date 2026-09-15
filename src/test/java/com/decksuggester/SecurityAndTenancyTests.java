package com.decksuggester;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

class SecurityAndTenancyTests {

    @Test
    void passwordHashesAreOneWayRandomlySaltedAndUseTheConfiguredPepper() {
        PasswordEncoder firstPepper = new Pbkdf2PasswordEncoder("first-pepper", 16, 1_000,
                PBKDF2WithHmacSHA256);
        PasswordEncoder otherPepper = new Pbkdf2PasswordEncoder("other-pepper", 16, 1_000,
                PBKDF2WithHmacSHA256);

        String firstHash = firstPepper.encode("a sufficiently long password");
        String secondHash = firstPepper.encode("a sufficiently long password");

        assertThat(firstHash).isNotEqualTo("a sufficiently long password")
                .isNotEqualTo(secondHash);
        assertThat(firstPepper.matches("a sufficiently long password", firstHash)).isTrue();
        assertThat(otherPepper.matches("a sufficiently long password", firstHash)).isFalse();
    }

    @Test
    void deckIdsAndOwnershipAreIsolatedByAccountEvenWhenTheArchidektIdMatches() {
        UserIdentity alice = new UserIdentity("account-a", "alice", "alice-library");
        UserIdentity bob = new UserIdentity("account-b", "bob", "bob-library");

        Deck aliceDeck = deck(alice);
        Deck bobDeck = deck(bob);

        assertThat(aliceDeck.getId()).isNotEqualTo(bobDeck.getId());
        assertThat(aliceDeck.getOwnerId()).isEqualTo("account-a");
        assertThat(aliceDeck.getLibraryId()).isEqualTo("alice-library");
    }

    @Test
    void cardImageProxyOnlyAcceptsHttpsScryfallHosts() {
        assertThat(ScryfallImageService.validatedScryfallUri(
                "https://cards.scryfall.io/normal/front/test.jpg").getHost())
                .isEqualTo("cards.scryfall.io");
        assertThatThrownBy(() -> ScryfallImageService.validatedScryfallUri(
                "https://example.com/private.jpg"))
                .isInstanceOf(CardImageNotFoundException.class);
        assertThatThrownBy(() -> ScryfallImageService.validatedScryfallUri(
                "http://cards.scryfall.io/test.jpg"))
                .isInstanceOf(CardImageNotFoundException.class);
    }

    private Deck deck(UserIdentity owner) {
        return new Deck(owner, 123, "Test", "", "https://archidekt.com/decks/123/test",
                10, "Root", 10, "Root", Instant.EPOCH, List.of());
    }
}
