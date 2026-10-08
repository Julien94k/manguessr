package com.manguessr.repository.projection;

/**
 * Les trois signaux de notoriete d'une oeuvre, pour son classement par difficulte.
 *
 * Projection plutot que calcul en SQL : la formule de {@code RecognitionScore} reste a un
 * seul endroit, testable, au lieu d'etre recopiee dans une requete.
 */
public record WorkAudience(Long id, Integer popularity, Integer favourites, Integer adaptationPopularity) {}
