package com.manguessr.service.catalog;

import java.util.List;

/**
 * Repartition des oeuvres en paliers de difficulte, par <b>rang</b> de popularite.
 *
 * Les paliers etaient des percentiles (15 % / 50 %) : agrandir le catalogue diluait alors
 * chaque palier, et doubler le catalogue envoyait le rang 300 en « facile » et le rang 2000 en
 * « difficile ». Un rang absolu garde un « facile » stable quelle que soit la taille du
 * catalogue, et le rang plafond du difficile permet d'ingerer plus d'oeuvres (autocompletion,
 * validation des reponses) sans les faire tirer : au-dela, une oeuvre n'a pas de palier et
 * n'entre dans aucun vivier de mode.
 *
 * @param easy     rangs 1 a easyMaxRank
 * @param medium   jusqu'a mediumMaxRank
 * @param hard     jusqu'a hardMaxRank
 * @param unranked au-dela : jamais tirees
 */
public record DifficultyTiers(List<Long> easy, List<Long> medium, List<Long> hard, List<Long> unranked) {

    /**
     * @param orderedIds identifiants tries par popularite decroissante
     */
    public static DifficultyTiers byRank(List<Long> orderedIds, int easyMaxRank, int mediumMaxRank, int hardMaxRank) {
        if (easyMaxRank > mediumMaxRank || mediumMaxRank > hardMaxRank) {
            throw new IllegalArgumentException("Rangs de paliers non croissants : "
                    + easyMaxRank + " / " + mediumMaxRank + " / " + hardMaxRank);
        }
        int size = orderedIds.size();
        int easyEnd = Math.min(easyMaxRank, size);
        int mediumEnd = Math.min(mediumMaxRank, size);
        int hardEnd = Math.min(hardMaxRank, size);
        return new DifficultyTiers(
                orderedIds.subList(0, easyEnd),
                orderedIds.subList(easyEnd, mediumEnd),
                orderedIds.subList(mediumEnd, hardEnd),
                orderedIds.subList(hardEnd, size));
    }
}
