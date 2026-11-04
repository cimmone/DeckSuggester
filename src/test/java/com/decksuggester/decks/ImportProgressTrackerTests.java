package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportProgressTrackerTests {

    private final ImportProgressTracker tracker = new ImportProgressTracker();
    private final UserIdentity alice = new UserIdentity("alice-id", "alice", "alice-library");
    private final UserIdentity bob = new UserIdentity("bob-id", "bob", "bob-library");

    @Test
    void tracksConcurrentImportsIndependentlyAndScopesThemToTheirOwner() {
        ImportProgress first = tracker.start(alice, "import-1");
        ImportProgress second = tracker.start(alice, "import-2");
        first.total(57);
        first.advance();
        second.total(3);

        assertEquals(1, tracker.find(alice, "import-1").orElseThrow().snapshot().completed());
        assertEquals(57, tracker.find(alice, "import-1").orElseThrow().snapshot().total());
        assertEquals(0, tracker.find(alice, "import-2").orElseThrow().snapshot().completed());
        assertTrue(tracker.find(bob, "import-1").isEmpty());
    }

    @Test
    void completionFillsTheBarAndFailureKeepsTheMessage() {
        ImportProgress done = tracker.start(alice, "ok");
        done.total(4);
        done.complete();
        assertEquals(ImportProgress.Status.COMPLETE, done.snapshot().status());
        assertEquals(4, done.snapshot().completed());

        ImportProgress failed = tracker.start(alice, "bad");
        failed.fail("Archidekt is down");
        assertEquals("Archidekt is down", failed.snapshot().error());
    }

    @Test
    void rejectsMalformedImportIds() {
        assertThrows(InvalidRequestException.class, () -> tracker.start(alice, "../etc"));
        assertThrows(InvalidRequestException.class, () -> tracker.start(alice, ""));
    }
}
