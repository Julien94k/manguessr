package com.manguessr.service.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Etat observable de l'ingestion en cours, expose par l'API pour suivre l'avancement
 * sans avoir a lire les logs du conteneur.
 */
public class IngestionStatus {

    private static final int MAX_LOG_LINES = 200;

    private volatile boolean running = false;
    private volatile String phase = "inactif";
    private volatile int processed = 0;
    private volatile int total = 0;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String error;

    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());

    public synchronized void start() {
        running = true;
        phase = "demarrage";
        processed = 0;
        total = 0;
        startedAt = Instant.now();
        finishedAt = null;
        error = null;
        logLines.clear();
    }

    public synchronized void finish(String failureMessage) {
        running = false;
        finishedAt = Instant.now();
        error = failureMessage;
        phase = failureMessage == null ? "termine" : "echec";
    }

    public void progress(String currentPhase, int done, int expected) {
        this.phase = currentPhase;
        this.processed = done;
        this.total = expected;
    }

    /** Journal borne : on ne garde que les dernieres lignes pour ne pas grossir indefiniment. */
    public void log(String line) {
        synchronized (logLines) {
            logLines.add(Instant.now().toString().substring(11, 19) + "  " + line);
            if (logLines.size() > MAX_LOG_LINES) {
                logLines.remove(0);
            }
        }
    }

    public boolean isRunning() { return running; }
    public String getPhase() { return phase; }
    public int getProcessed() { return processed; }
    public int getTotal() { return total; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getError() { return error; }

    public List<String> getLogLines() {
        synchronized (logLines) {
            return List.copyOf(logLines);
        }
    }
}
