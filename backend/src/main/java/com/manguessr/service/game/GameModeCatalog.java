package com.manguessr.service.game;

import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;

/**
 * Libelles des modes, cote serveur.
 *
 * Le front n'a ainsi pas a dupliquer les regles : il affiche ce que l'API annonce, et un
 * changement de bareme ne peut pas creer d'ecart entre la description et le comportement reel.
 */
public final class GameModeCatalog {

    private GameModeCatalog() {}

    public static String title(GameMode mode, WorkType theme) {
        return switch (mode) {
            case IMAGES -> "Devinez avec une image";
            case CHARACTERS -> "Devinez les personnages";
            case OPENING -> "Devinez l'opening";
            case ENDING -> "Devinez l'ending";
            case WORDLE -> theme == WorkType.ANIME ? "Anidle" : "Mangadle";
            case COVER_REVEAL -> "Couverture floutée";
        };
    }

    public static String description(GameMode mode, WorkType theme) {
        return switch (mode) {
            case IMAGES -> theme == WorkType.ANIME
                    ? "Trois images d'épisodes, de plus en plus parlantes."
                    : "Trois pages du manga, de plus en plus parlantes.";
            case CHARACTERS -> "Quatre portraits : nom du personnage et titre de l'œuvre.";
            case OPENING -> "L'audio d'abord, le clip vidéo si vous séchez.";
            case ENDING -> "Même principe, avec une jaquette floutée en prime.";
            case WORDLE -> "Déduisez l'œuvre du jour à partir de vos essais.";
            case COVER_REVEAL -> "La jaquette se défloute à chaque essai. Dix tentatives.";
        };
    }
}
