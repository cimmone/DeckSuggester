package com.decksuggester.decks;

import com.decksuggester.InvalidRequestException;
import com.decksuggester.auth.UserIdentity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Tracks in-flight imports by client-chosen id, scoped to the owning user so one user can't read another's progress. */
@Component
public class ImportProgressTracker {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Duration FINISHED_RETENTION = Duration.ofMinutes(10);

    private final Map<String, ImportProgress> imports = new ConcurrentHashMap<>();

    public ImportProgress start(UserIdentity owner, String importId) {
        if (importId == null || !VALID_ID.matcher(importId).matches()) {
            throw new InvalidRequestException("Invalid import id");
        }
        purgeFinished();
        ImportProgress progress = new ImportProgress();
        imports.put(key(owner, importId), progress);
        return progress;
    }

    public Optional<ImportProgress> find(UserIdentity owner, String importId) {
        return Optional.ofNullable(imports.get(key(owner, importId)));
    }

    private void purgeFinished() {
        Instant cutoff = Instant.now().minus(FINISHED_RETENTION);
        imports.values().removeIf(progress ->
                progress.finishedAt() != null && progress.finishedAt().isBefore(cutoff));
    }

    private static String key(UserIdentity owner, String importId) {
        return owner.ownerId() + ":" + owner.libraryId() + ":" + importId;
    }
}
