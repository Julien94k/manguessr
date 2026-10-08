package com.manguessr.service.catalog;

import com.manguessr.model.enums.WorkType;
import com.manguessr.service.catalog.dto.CatalogPage;

/**
 * Source de metadonnees d'oeuvres.
 *
 * L'interface isole le moteur de jeu du fournisseur : basculer d'AniList vers une autre
 * source ne doit toucher aucune logique de jeu. Le choix se fait par la propriete
 * {@code app.catalog.provider}.
 */
public interface MediaCatalogProvider {

    /** Identifiant utilise dans {@code app.catalog.provider}. */
    String name();

    /**
     * Recupere une page d'oeuvres triees par popularite decroissante.
     *
     * @param type    univers a ingerer
     * @param page    numero de page, commence a 1
     * @param perPage taille de page
     */
    CatalogPage fetchPage(WorkType type, int page, int perPage);
}
