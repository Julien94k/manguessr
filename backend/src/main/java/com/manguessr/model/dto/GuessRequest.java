package com.manguessr.model.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Une tentative du joueur.
 *
 * @param roundOrdinal manche visee
 * @param answer       reponse unique, pour tous les modes sauf Personnages
 * @param entries      quatre couples nom/titre, pour le mode Personnages
 */
public record GuessRequest(
        @Min(value = 0, message = "Manche invalide.")
        int roundOrdinal,
        String answer,
        List<CharacterGuess> entries) {

    /**
     * Reponse sur un portrait. Les deux champs sont facultatifs : AniGuessr autorise
     * a laisser un portrait vide quand on ne sait pas.
     */
    public record CharacterGuess(String character, String title) {}
}
