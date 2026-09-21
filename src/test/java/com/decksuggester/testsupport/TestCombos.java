package com.decksuggester.testsupport;

import com.decksuggester.spellbook.Combo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Builds {@link Combo} documents for seeding a real (embedded) Mongo instance
 * in end-to-end tests. {@link Combo} has no public constructor for its
 * persisted fields (it is populated by the variants.json import), so this
 * sets its private fields via reflection.
 */
public final class TestCombos {

    private TestCombos() {
    }

    public static Combo combo(String identity, boolean commanderLegal, Combo.Use... uses) {
        Combo combo = new Combo();
        set(combo, "identity", identity);
        set(combo, "uses", List.of(uses));
        set(combo, "legalities", new Combo.ComboLegalities(
                commanderLegal, true, true, true, true, true));
        return combo;
    }

    public static Combo.Use use(String name, String oracleId, String typeLine) {
        return new Combo.Use(new Combo.Card(1L, name, oracleId, typeLine, null), 1);
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
