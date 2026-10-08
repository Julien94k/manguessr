package com.manguessr.model.dto;

/**
 * Etat d'un mode dans le defi du jour.
 *
 * @param status    NOT_STARTED, IN_PROGRESS ou FINISHED ; toujours NOT_STARTED sans compte
 * @param sessionId partie du jour du joueur, s'il l'a commencee
 */
public record DailyModeView(
        String mode,
        String title,
        String status,
        Long sessionId,
        int score,
        int maxScore) {}
