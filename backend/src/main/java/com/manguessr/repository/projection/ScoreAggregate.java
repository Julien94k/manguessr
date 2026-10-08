package com.manguessr.repository.projection;

/**
 * Score cumule d'un joueur sur une periode, pour le classement.
 *
 * @param userId   identifiant du joueur
 * @param username pseudo public
 * @param score    somme des scores des parties quotidiennes
 * @param games    nombre de parties comptees
 */
public record ScoreAggregate(Long userId, String username, Long score, Long games) {}
