package com.manguessr.model.dto;

import java.util.List;

/**
 * Une ligne de la table de comparaison, correspondant a un essai.
 *
 * @param title   oeuvre proposee
 * @param correct l'essai etait la bonne reponse
 * @param year    annee, avec fleche de comparaison
 * @param credit  studio (anime) ou auteur (manga)
 * @param source  oeuvre d'origine
 * @param score   note, avec fleche de comparaison
 * @param genres  genres, un par case
 * @param tags    tags, un par case
 */
public record WordleRowView(
        String title,
        boolean correct,
        WordleCellView year,
        List<WordleCellView> credit,
        WordleCellView source,
        WordleCellView score,
        List<WordleCellView> genres,
        List<WordleCellView> tags) {}
