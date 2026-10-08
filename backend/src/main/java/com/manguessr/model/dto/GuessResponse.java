package com.manguessr.model.dto;

/**
 * Verdict d'une tentative.
 *
 * @param correct  la reponse etait la bonne
 * @param message  precision affichable, par exemple un doublon dans le mode Wordle
 * @param round    nouvel etat de la manche
 * @param session  nouvel etat de la partie, pour rafraichir le score total
 */
public record GuessResponse(boolean correct, String message, RoundView round, SessionView session) {}
