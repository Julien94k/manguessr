package com.manguessr.model.enums;

import java.util.Locale;

/** Univers auquel appartient une oeuvre. Pilote les modes de jeu disponibles. */
public enum WorkType {
    ANIME,
    MANGA;

    /**
     * Lit un parametre de requete ({@code anime}, {@code MANGA}...).
     *
     * @throws IllegalArgumentException avec un message affichable si la valeur est inconnue
     */
    public static WorkType fromParam(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Message plus parlant ci-dessous que celui de valueOf.
            }
        }
        throw new IllegalArgumentException("Univers inconnu : '" + value + "'. Attendu : anime ou manga.");
    }
}
