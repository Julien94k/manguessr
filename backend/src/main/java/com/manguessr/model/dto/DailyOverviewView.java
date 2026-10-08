package com.manguessr.model.dto;

import java.util.List;

/**
 * Defi du jour d'un univers.
 *
 * @param date             jour en cours, en UTC
 * @param secondsUntilNext secondes avant le prochain defi (minuit UTC)
 * @param totalScore       score du jour du joueur sur cet univers
 * @param maxScore         total atteignable sur cet univers
 * @param currentStreak    serie du joueur connecte ; nulle sans compte
 */
public record DailyOverviewView(
        String date,
        long secondsUntilNext,
        String theme,
        int totalScore,
        int maxScore,
        Integer currentStreak,
        List<DailyModeView> modes) {}
