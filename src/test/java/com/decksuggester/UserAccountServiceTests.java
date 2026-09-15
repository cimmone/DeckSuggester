package com.decksuggester;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAccountServiceTests {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserAccountService service = new UserAccountService(repository, passwordEncoder,
            "test-pepper", 30, "http://localhost:8080");

    @Test
    void registrationAutoGeneratesAUniqueLibraryIdWithoutOneBeingSupplied() {
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.register("newplayer", "a very long password", "player@example.com");

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLibraryId()).isNotBlank();
        assertThat(saved.getValue().getUsername()).isEqualTo("newplayer");
    }

    @Test
    void aTakenUsernameIsRejectedBeforeAllocatingAnyLibraryId() {
        when(repository.existsByUsernameKey("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.register("taken", "a very long password",
                "player@example.com"))
                .isInstanceOf(AccountConflictException.class);
    }
}
