package com.manguessr.model.enums;

/** Nature d'une image, qui determine le mode de jeu ou elle peut servir. */
public enum ImageKind {
    /** Vignette d'episode AniList (streamingEpisodes) — mode Images cote anime. */
    EPISODE_THUMB,
    /**
     * Couverture de volume MangaDex. Plus utilisee par le mode Images : le titre imprime y
     * restait lisible sur environ un recadrage sur trois. Conservee a l'ingestion.
     */
    VOLUME_COVER,
    /** Page interieure d'un chapitre MangaDex — mode Images cote manga. Ne porte pas le titre. */
    CHAPTER_PAGE,
    /** Banniere large AniList — repli du mode Images. */
    BANNER,
    /** Jaquette principale — mode Cover-deblur et indice du mode Wordle. */
    COVER
}
