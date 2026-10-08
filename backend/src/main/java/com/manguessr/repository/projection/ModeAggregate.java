package com.manguessr.repository.projection;

import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;

/**
 * Parties quotidiennes terminees d'un joueur, agregees par mode et univers.
 *
 * @param played       nombre de parties
 * @param totalScore   somme des scores
 * @param bestScore    meilleur score
 * @param averageScore score moyen
 */
public record ModeAggregate(
        GameMode mode,
        WorkType theme,
        Long played,
        Long totalScore,
        Integer bestScore,
        Double averageScore) {}
