package com.manguessr.service.catalog.dto;

/**
 * Un lien entre deux oeuvres du meme univers, retenu pour le regroupement en series.
 *
 * Le lien est symetrique pour le regroupement ; sa nature ne compte que lorsqu'il mene a une
 * oeuvre hors catalogue, qui ne sert de pont qu'a certaines conditions (voir
 * {@code SeriesGrouper}).
 *
 * @param fromAnilistId oeuvre qui porte la relation
 * @param toAnilistId   oeuvre liee
 * @param relationType  nature AniList du lien (SEQUEL, SPIN_OFF...)
 */
public record WorkRelation(int fromAnilistId, int toAnilistId, String relationType) {

    /** Suite ou prequelle : le meme recit se poursuit. */
    public boolean isContinuity() {
        return "SEQUEL".equals(relationType) || "PREQUEL".equals(relationType);
    }
}
