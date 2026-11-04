package com.decksuggester.decks;

import java.time.Instant;

/** Live, thread-safe progress of one import, written by the import and read by the progress endpoint. */
public class ImportProgress {

    public enum Status { RUNNING, COMPLETE, FAILED }

    /** Phases are "starting", "discovering" (walking folders), "fetching" (decks), "saving". */
    private volatile Status status = Status.RUNNING;
    private volatile String phase = "starting";
    private volatile String label;
    private volatile int total;
    private volatile int completed;
    private volatile String error;
    private volatile Instant finishedAt;

    public void phase(String phase) {
        this.phase = phase;
    }

    public void label(String label) {
        this.label = label;
    }

    public void total(int total) {
        this.total = total;
    }

    public synchronized void advance() {
        completed++;
    }

    public void complete() {
        completed = total;
        phase = "done";
        status = Status.COMPLETE;
        finishedAt = Instant.now();
    }

    public void fail(String message) {
        error = message;
        status = Status.FAILED;
        finishedAt = Instant.now();
    }

    Instant finishedAt() {
        return finishedAt;
    }

    public Snapshot snapshot() {
        return new Snapshot(status, phase, label, total, completed, error);
    }

    public record Snapshot(Status status, String phase, String label, int total, int completed,
                           String error) {
    }
}
