package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoxfieldClientTests {

    @Test
    void extractsThePublicIdFromAMoxfieldDeckUrl() {
        assertThat(MoxfieldClient.extractPublicId(
                "https://moxfield.com/decks/A63Mp5u1_UqaBpoMmJDXYg"))
                .isEqualTo("A63Mp5u1_UqaBpoMmJDXYg");
    }

    @Test
    void rejectsUrlsWithoutADecksPathSegment() {
        assertThatThrownBy(() -> MoxfieldClient.extractPublicId(
                "https://moxfield.com/users/someone"))
                .isInstanceOf(InvalidRequestException.class);
    }
}
