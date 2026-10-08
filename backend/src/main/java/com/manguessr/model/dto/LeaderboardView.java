package com.manguessr.model.dto;

import java.util.List;

/**
 * Classement d'une periode.
 *
 * @param period  daily, weekly ou alltime
 * @param theme   anime, manga ou all
 * @param from    premier jour compte, en UTC
 * @param to      dernier jour compte, en UTC
 * @param entries meilleurs joueurs, dans l'ordre
 * @param me      ligne du joueur connecte, meme hors du haut du classement ; nulle sinon
 */
public record LeaderboardView(
        String period,
        String theme,
        String from,
        String to,
        List<LeaderboardEntryView> entries,
        LeaderboardEntryView me) {}
