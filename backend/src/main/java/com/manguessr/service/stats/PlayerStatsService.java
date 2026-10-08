package com.manguessr.service.stats;

import com.manguessr.model.dto.*;
import com.manguessr.model.entity.GameSession;
import com.manguessr.model.entity.User;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.SessionStatus;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.GameSessionRepository;
import com.manguessr.repository.projection.ModeAggregate;
import com.manguessr.service.game.GameModeCatalog;
import com.manguessr.service.game.ScoreCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** Serie, statistiques de profil et etat du defi du jour. */
@Service
public class PlayerStatsService {

    private final GameSessionRepository sessionRepository;
    private final ScoreCalculator scoreCalculator;
    private final Clock clock;

    public PlayerStatsService(GameSessionRepository sessionRepository,
                              ScoreCalculator scoreCalculator,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.scoreCalculator = scoreCalculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlayerStatsView statsFor(User user) {
        LocalDate today = today();
        List<LocalDate> days = sessionRepository.findDailyPlayDates(user, SessionStatus.FINISHED);

        long gamesPlayed = 0;
        long totalScore = 0;
        List<ModeStatsView> modes = new ArrayList<>();
        for (ModeAggregate aggregate : sessionRepository.aggregateDailyByMode(user, SessionStatus.FINISHED)) {
            gamesPlayed += aggregate.played();
            totalScore += aggregate.totalScore();
            modes.add(new ModeStatsView(
                    aggregate.mode().name(),
                    aggregate.theme().name(),
                    GameModeCatalog.title(aggregate.mode(), aggregate.theme()),
                    aggregate.played(),
                    aggregate.bestScore(),
                    (int) Math.round(aggregate.averageScore()),
                    maxScoreOf(aggregate.mode())));
        }
        modes.sort(Comparator.comparing(ModeStatsView::theme).thenComparing(ModeStatsView::mode));

        List<HistoryEntryView> history = sessionRepository
                .findTop10ByUserAndUnlimitedFalseAndStatusOrderByPlayDateDescIdDesc(user, SessionStatus.FINISHED)
                .stream()
                .map(session -> new HistoryEntryView(
                        session.getId(),
                        session.getPlayDate().toString(),
                        session.getMode().name(),
                        session.getTheme().name(),
                        GameModeCatalog.title(session.getMode(), session.getTheme()),
                        session.getTotalScore(),
                        maxScoreOf(session.getMode())))
                .toList();

        return new PlayerStatsView(
                StreakCalculator.current(days, today),
                StreakCalculator.best(days),
                days.size(),
                gamesPlayed,
                totalScore,
                modes,
                history);
    }

    /**
     * Defi du jour d'un univers, avec la progression du joueur s'il est connecte.
     *
     * Un joueur anonyme recoit tous les modes a NOT_STARTED : le serveur ne peut pas relier
     * ses parties a lui.
     */
    @Transactional(readOnly = true)
    public DailyOverviewView dailyOverview(WorkType theme, User user) {
        LocalDate today = today();
        Instant nextPuzzle = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long secondsUntilNext = Math.max(0, Duration.between(Instant.now(clock), nextPuzzle).getSeconds());

        Map<GameMode, GameSession> todays = new EnumMap<>(GameMode.class);
        if (user != null) {
            for (GameSession session : sessionRepository.findByUserAndPlayDateAndUnlimitedFalse(user, today)) {
                if (session.getTheme() == theme) {
                    todays.putIfAbsent(session.getMode(), session);
                }
            }
        }

        int totalScore = 0;
        int maxScore = 0;
        List<DailyModeView> modes = new ArrayList<>();
        for (GameMode mode : GameMode.forType(theme)) {
            GameSession session = todays.get(mode);
            String status = session == null
                    ? "NOT_STARTED"
                    : session.getStatus() == SessionStatus.FINISHED ? "FINISHED" : "IN_PROGRESS";
            int score = session == null ? 0 : session.getTotalScore();

            totalScore += score;
            maxScore += maxScoreOf(mode);
            modes.add(new DailyModeView(
                    mode.name(),
                    GameModeCatalog.title(mode, theme),
                    status,
                    session == null ? null : session.getId(),
                    score,
                    maxScoreOf(mode)));
        }

        Integer streak = user == null
                ? null
                : StreakCalculator.current(sessionRepository.findDailyPlayDates(user, SessionStatus.FINISHED), today);

        return new DailyOverviewView(
                today.toString(),
                secondsUntilNext,
                theme.name().toLowerCase(Locale.ROOT),
                totalScore,
                maxScore,
                streak,
                modes);
    }

    private int maxScoreOf(GameMode mode) {
        return scoreCalculator.maxScoreFor(mode) * mode.getRoundsPerPuzzle();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
