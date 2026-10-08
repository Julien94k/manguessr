package com.manguessr.repository.projection;

import com.manguessr.model.enums.TitleKind;

/**
 * Un titre de l'index d'autocompletion, sa nature et son oeuvre.
 *
 * L'identifiant d'oeuvre est indispensable : un synonyme n'est garde que s'il est une variante
 * du titre romaji ou anglais <b>de son oeuvre</b> (voir {@code SuggestibleTitles}).
 *
 * L'identifiant AniList et celui de la serie servent, eux, a reperer l'oeuvre de reference
 * d'une franchise : c'est celle dont {@code anilistId} vaut {@code seriesId}.
 */
public record TitleEntry(Long workId, Integer anilistId, Integer seriesId, String value, TitleKind kind) {

    /** Cette oeuvre est-elle celle qui nomme sa serie ? */
    public boolean isSeriesLeader() {
        return anilistId != null && anilistId.equals(seriesId);
    }
}
