package com.manguessr.model.dto;

import java.util.List;

/**
 * Statistiques d'un joueur.
 *
 * @param currentStreak jours consecutifs avec au moins une partie quotidienne terminee ;
 *                      la serie reste active tant que la journee en cours n'est pas finie
 * @param bestStreak    plus longue serie
 * @param daysPlayed    jours avec au moins une partie quotidienne terminee
 * @param gamesPlayed   parties quotidiennes terminees
 * @param totalScore    somme de leurs scores
 * @param modes         detail par mode et univers
 * @param history       dernieres parties terminees
 */
public record PlayerStatsView(
        int currentStreak,
        int bestStreak,
        int daysPlayed,
        long gamesPlayed,
        long totalScore,
        List<ModeStatsView> modes,
        List<HistoryEntryView> history) {}
