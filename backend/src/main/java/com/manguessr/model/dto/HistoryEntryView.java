package com.manguessr.model.dto;

/** Une partie quotidienne terminee, pour l'historique du profil. */
public record HistoryEntryView(
        Long sessionId,
        String playDate,
        String mode,
        String theme,
        String title,
        int score,
        int maxScore) {}
