package com.decksuggester.migrations;

import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Wrapper used to hand the spellbook {@link MongoTemplate} to a Flamingock
 * change as an injected dependency. Flamingock resolves {@code @Apply}
 * parameters by type from its registered dependencies, and the suggester
 * template is already registered under {@code MongoTemplate.class}; this
 * distinct wrapper type lets a change receive the spellbook template (which
 * points at the separate combo {@code variants} collection) without a type
 * clash.
 */
public record SpellbookIndexTarget(MongoTemplate spellbookTemplate) {
}
