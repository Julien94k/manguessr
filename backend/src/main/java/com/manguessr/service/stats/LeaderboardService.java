package com.manguessr.service.stats;

import com.manguessr.model.dto.LeaderboardEntryView;
import com.manguessr.model.dto.LeaderboardView;
import com.manguessr.model.entity.User;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.GameSessionRepository;
import com.manguessr.repository.projection.ScoreAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Classement par periode : somme des scores des parties quotidiennes.
 *
 * Les parties libres et anonymes n'y figurent jamais.
 */
@Service
public class LeaderboardService {

    /** Taille du classement affiche. */
    static final int TOP = 50;

    /** Borne basse de la periode « tout temps ». */
    private static final LocalDate ORIGIN = LocalDate.of(2000, 1, 1);

    private final GameSessionRepository sessionRepository;
    private final Clock clock;

    public LeaderboardService(GameSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /**
     * @param period daily (aujourd'hui), weekly (depuis lundi) ou alltime
     * @param theme  univers, ou {@code null} pour les deux
     * @param user   joueur connecte, ou {@code null}
     */
    @Transactional(readOnly = true)
    public LeaderboardView leaderboard(String period, WorkType theme, User user) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String normalizedPeriod = period == null ? "daily" : period.trim().toLowerCase(Locale.ROOT);

        LocalDate from = switch (normalizedPeriod) {
            case "daily" -> today;
            case "weekly" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "alltime" -> ORIGIN;
            default -> throw new IllegalArgumentException(
                    "Période inconnue : '" + period + "'. Attendu : daily, weekly ou alltime.");
        };

        List<ScoreAggregate> rows = theme == null
                ? sessionRepository.leaderboard(from, today)
                : sessionRepository.leaderboardForTheme(from, today, theme);

        List<LeaderboardEntryView> ranked = rank(rows);

        LeaderboardEntryView me = null;
        if (user != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).userId().equals(user.getId())) {
                    me = ranked.get(i);
                    break;
                }
            }
        }

        return new LeaderboardView(
                normalizedPeriod,
                theme == null ? "all" : theme.name().toLowerCase(Locale.ROOT),
                from.toString(),
                today.toString(),
                ranked.subList(0, Math.min(TOP, ranked.size())),
                me);
    }

    /**
     * Rangs « a la competition » : deux scores egaux partagent un rang, et le suivant saute
     * d'autant (1, 2, 2, 4). Les lignes doivent arriver triees par score decroissant.
     */
    static List<LeaderboardEntryView> rank(List<ScoreAggregate> rows) {
        List<LeaderboardEntryView> ranked = new ArrayList<>(rows.size());
        int rank = 0;
        Long previousScore = null;

        for (int i = 0; i < rows.size(); i++) {
            ScoreAggregate row = rows.get(i);
            if (!row.score().equals(previousScore)) {
                rank = i + 1;
                previousScore = row.score();
            }
            ranked.add(new LeaderboardEntryView(rank, row.username(), row.score(), row.games()));
        }
        return ranked;
    }
}
