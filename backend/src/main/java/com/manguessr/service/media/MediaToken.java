package com.manguessr.service.media;

import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;

/**
 * Contenu d'un jeton media signe.
 *
 * @param source    table d'origine du media
 * @param id        identifiant dans cette table
 * @param transform traitement a appliquer
 * @param parameter palier de flou ou graine de recadrage, selon le traitement
 * @param expiresAt expiration, en secondes depuis l'epoch
 */
public record MediaToken(
        MediaSource source,
        long id,
        MediaTransform transform,
        int parameter,
        long expiresAt) {

    /**
     * Jeton servant uniquement a rendre une image, jamais a la servir.
     *
     * L'expiration n'a de sens que dans une URL signee ; le rendu et sa cle de cache n'en
     * dependent pas, donc un prechauffage produit bien le fichier que la requete du joueur
     * trouvera ensuite.
     */
    public static MediaToken forRender(MediaSource source, long id, MediaTransform transform, int parameter) {
        return new MediaToken(source, id, transform, parameter, 0L);
    }
}
