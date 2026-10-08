package com.manguessr.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Demande de demarrage d'une partie.
 *
 * @param mode      mode de jeu
 * @param theme     univers
 * @param unlimited partie libre : tirage aleatoire, hors puzzle quotidien et hors classement
 */
public record StartSessionRequest(
        @NotBlank(message = "Le mode est obligatoire.")
        String mode,
        @NotBlank(message = "L'univers est obligatoire.")
        String theme,
        boolean unlimited) {}
