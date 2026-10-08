package com.manguessr.service.game;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Prepare les puzzles quotidiens a l'avance.
 *
 * Non indispensable au fonctionnement — une partie genere son puzzle s'il manque — mais
 * evite au premier joueur du jour d'attendre le tirage, et fige le lendemain des la veille.
 */
@Component
@ConditionalOnProperty(name = "app.daily.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class DailyPuzzleScheduler {

    private final DailyPuzzleGenerator generator;
    private final Clock clock;

    public DailyPuzzleScheduler(DailyPuzzleGenerator generator, Clock clock) {
        this.generator = generator;
        this.clock = clock;
    }

    /** Rattrapage au demarrage : le Pi a pu etre eteint a l'heure du cron. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        LocalDate today = today();
        generator.generateAll(today);
        // Les puzzles generes avant ce demarrage n'ont pas ete prechauffes : on rattrape aussi
        // leurs images, sans quoi le premier joueur du jour attendrait le CDN d'origine.
        generator.warmExisting(today);
    }

    @Scheduled(cron = "${app.daily.cron:0 5 0 * * *}", zone = "UTC")
    public void prepareUpcoming() {
        LocalDate today = today();
        generator.generateAll(today);
        generator.generateAll(today.plusDays(1));
        generator.warmExisting(today);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
