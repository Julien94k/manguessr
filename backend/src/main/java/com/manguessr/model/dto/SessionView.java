package com.manguessr.model.dto;

import java.util.List;

/**
 * Etat complet d'une partie.
 *
 * @param id         identifiant de la partie
 * @param mode       mode de jeu
 * @param theme      univers
 * @param unlimited  partie libre, hors puzzle quotidien
 * @param playDate   jour de jeu, en UTC
 * @param status     IN_PROGRESS ou FINISHED
 * @param totalScore somme des scores des manches, recalculee cote serveur
 * @param maxScore   total atteignable sur la partie
 * @param rounds     manches, dans l'ordre
 */
public record SessionView(
        Long id,
        String mode,
        String theme,
        boolean unlimited,
        String playDate,
        String status,
        int totalScore,
        int maxScore,
        List<RoundView> rounds) {}
