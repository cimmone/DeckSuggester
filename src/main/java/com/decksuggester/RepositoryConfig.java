package com.decksuggester;

import com.decksuggester.auth.UserAccountRepository;
import com.decksuggester.cards.CardRepository;
import com.decksuggester.decks.DeckHistoryRepository;
import com.decksuggester.decks.DeckRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Binds each repository family to the Mongo database that owns its collections.
 *
 * <ul>
 *   <li>{@link CardRepository} (cards feature) &rarr; scryfall database.</li>
 *   <li>{@link DeckRepository}, {@link DeckHistoryRepository} (decks feature) and
 *       {@link UserAccountRepository} (auth feature) &rarr; suggester database.</li>
 *   <li>{@link com.decksuggester.spellbook.ComboRepository} &rarr; spellbook
 *       database.</li>
 * </ul>
 *
 * The repositories live across several feature packages, so we bind them
 * explicitly with {@code basePackageClasses} rather than by package sweep,
 * keeping each family on its correct template.
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
                    UserAccountRepository.class,
                    DeckHistoryRepository.class
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
