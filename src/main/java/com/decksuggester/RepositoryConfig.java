package com.decksuggester;

import com.decksuggester.auth.UserAccountRepository;
import com.decksuggester.cards.CardRepository;
import com.decksuggester.decks.DeckHistoryRepository;
import com.decksuggester.decks.DeckRepository;
import com.decksuggester.folders.FolderRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Binds each repository family to the Mongo database that owns its collections, using
 * {@code basePackageClasses} rather than a package sweep since the repositories span
 * several feature packages but must each land on a specific template.
 */
@Configuration
public class RepositoryConfig {

    @Configuration
    @EnableMongoRepositories(
            basePackageClasses = CardRepository.class,
            mongoTemplateRef = "scryfallMongoTemplate")
    static class CardRepositories {
    }

    @Configuration
    @EnableMongoRepositories(
            basePackageClasses = {
                    DeckRepository.class,
                    DeckHistoryRepository.class,
                    FolderRepository.class,
                    UserAccountRepository.class
            },
            mongoTemplateRef = "suggesterMongoTemplate")
    static class SuggesterRepositories {
    }

    @Configuration
    @EnableMongoRepositories(
            basePackageClasses = com.decksuggester.spellbook.ComboRepository.class,
            mongoTemplateRef = "spellbookMongoTemplate")
    static class SpellbookRepositories {
    }

}
