package com.manguessr.model.enums;

/** Traitement applique par le proxy avant de servir une image. */
public enum MediaTransform {

    /** Redimensionnement seul : portraits de personnages, vignettes d'episodes. */
    RAW,

    /**
     * Flou decroissant du mode Cover-deblur. Le parametre porte le palier (0 = illisible).
     * Applique cote serveur : un flou CSS se retire dans les devtools, or ici le flou est le jeu.
     */
    BLUR,

    /**
     * Recadrage aleatoire deterministe. Le parametre porte la graine.
     * Sert aux couvertures de volumes manga, qui portent le titre imprime.
     */
    CROP
}
