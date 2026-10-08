package com.manguessr.service.game.mode;

import java.util.List;

/**
 * Elements tires pour une manche, figes a sa creation, et resultats du joueur.
 *
 * Les identifiants sont conserves plutot que les objets : une manche jouee hier doit rester
 * identique meme si une reingestion a modifie le catalogue entre-temps.
 *
 * @param imageIds     images retenues, dans l'ordre de devoilement
 * @param characterIds portraits du mode Personnages, un par oeuvre distincte
 * @param themeId      generique des modes Opening et Ending
 * @param cropSeed     graine de recadrage, pour que le cadrage soit reproductible
 * @param nameFound    par portrait, le nom a-t-il ete trouve
 * @param titleFound   par portrait, le titre a-t-il ete trouve
 */
public record RoundPayload(
        List<Long> imageIds,
        List<Long> characterIds,
        Long themeId,
        int cropSeed,
        List<Boolean> nameFound,
        List<Boolean> titleFound) {

    public static RoundPayload images(List<Long> imageIds, int cropSeed) {
        return new RoundPayload(imageIds, List.of(), null, cropSeed, List.of(), List.of());
    }

    public static RoundPayload characters(List<Long> characterIds) {
        return new RoundPayload(List.of(), characterIds, null, 0, List.of(), List.of());
    }

    public static RoundPayload theme(Long themeId) {
        return new RoundPayload(List.of(), List.of(), themeId, 0, List.of(), List.of());
    }

    public static RoundPayload empty() {
        return new RoundPayload(List.of(), List.of(), null, 0, List.of(), List.of());
    }

    /** Enregistre le detail des bonnes reponses, portrait par portrait. */
    public RoundPayload withResults(List<Boolean> names, List<Boolean> titles) {
        return new RoundPayload(imageIds, characterIds, themeId, cropSeed, names, titles);
    }

    /** Vrai si le portrait a ce rang a ete nomme correctement. */
    public boolean isNameFound(int slot) {
        return slot < nameFound.size() && Boolean.TRUE.equals(nameFound.get(slot));
    }

    public boolean isTitleFound(int slot) {
        return slot < titleFound.size() && Boolean.TRUE.equals(titleFound.get(slot));
    }
}
