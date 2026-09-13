package com.decksuggester;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchidektClientTests {

    @Test
    void createsNextDataDeckSlugs() {
        assertThat(ArchidektClient.slugify("Abilities Tribal"))
                .isEqualTo("abilities_tribal");
        assertThat(ArchidektClient.slugify("Rin & Seri: Friends"))
                .isEqualTo("rin_seri_friends");
    }
}
