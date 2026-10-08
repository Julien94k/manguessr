package com.manguessr.model.dto;

/**
 * Une case de la table de comparaison du mode Wordle.
 *
 * @param value   valeur proposee par le joueur
 * @param status  MATCH (vert), PARTIAL (jaune, tag secondaire), MISS (rouge)
 * @param compare "UP" ou "DOWN" sur l'annee et le score, sinon nul
 */
public record WordleCellView(String value, String status, String compare) {}
