package com.manguessr.model.dto;

/**
 * Description d'un mode, pour l'ecran d'accueil.
 *
 * @param id          identifiant technique
 * @param title       libelle affiche
 * @param description regle en une phrase
 * @param rounds      nombre de manches
 * @param maxScore    points maximum par manche
 * @param maxAttempts essais autorises par manche
 * @param playable    le catalogue contient assez d'oeuvres pour jouer ce mode
 */
public record GameModeView(
        String id,
        String title,
        String description,
        int rounds,
        int maxScore,
        int maxAttempts,
        boolean playable) {}
