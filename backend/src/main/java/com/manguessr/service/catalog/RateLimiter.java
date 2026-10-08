package com.manguessr.service.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Etalement simple des appels sortants : garantit un intervalle minimum entre deux requetes.
 *
 * AniList est plafonne a 30 requetes/minute (regime degrade), soit 2 s entre deux appels.
 * MangaDex tolere ~5 req/s. Un thread d'ingestion unique par source suffit donc a tenir
 * le quota sans machinerie de token bucket.
 *
 * Cette classe n'est pas thread-safe au sens d'un partage entre ingestions concurrentes :
 * elle est concue pour etre utilisee par un seul thread d'ingestion a la fois, ce que
 * garantit le verrou de CatalogIngestionService.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final String name;
    private final long minIntervalMs;
    private long lastCallAt = 0L;

    public RateLimiter(String name, Duration minInterval) {
        this.name = name;
        this.minIntervalMs = minInterval.toMillis();
    }

    /** Bloque le temps necessaire pour respecter l'intervalle minimum. */
    public void acquire() {
        long waitMs = (lastCallAt + minIntervalMs) - System.currentTimeMillis();
        if (waitMs > 0) {
            sleep(waitMs);
        }
        lastCallAt = System.currentTimeMillis();
    }

    /**
     * Applique une pause imposee par le serveur (en-tete Retry-After) et repousse
     * d'autant la prochaine fenetre.
     */
    public void backOff(Duration duration) {
        log.warn("[{}] Pause imposee par le serveur : {} s", name, duration.toSeconds());
        sleep(duration.toMillis());
        lastCallAt = System.currentTimeMillis();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ingestion interrompue", e);
        }
    }
}
