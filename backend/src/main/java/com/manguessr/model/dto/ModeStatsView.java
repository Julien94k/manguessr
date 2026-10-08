package com.manguessr.model.dto;

/**
 * Statistiques d'un joueur sur un mode et un univers (parties quotidiennes terminees).
 *
 * @param maxScore score maximum d'une partie de ce mode, pour situer les autres valeurs
 */
public record ModeStatsView(
        String mode,
        String theme,
        String title,
        long played,
        int bestScore,
        int averageScore,
        int maxScore) {}
