package com.manguessr.service.catalog;

import com.manguessr.service.catalog.dto.WorkRelation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le regroupement en series decide de ce qui vaut une bonne reponse : un groupe trop large
 * offrirait des points, un groupe trop etroit ferait perdre un joueur qui a reconnu l'oeuvre.
 * Les identifiants et titres sont ceux des vraies fiches AniList citees.
 */
class SeriesGrouperTest {

    private static final int AOT_S1 = 16498;
    private static final int AOT_S2 = 25777;
    private static final int AOT_S3 = 99147;
    private static final int DEATH_NOTE = 1535;
    private static final int FAIRY_TAIL = 30598;
    private static final int SEVEN_DEADLY_SINS = 74485;
    private static final int CROSSOVER = 85257;
    private static final int BAHAMUT_GENESIS = 20590;
    private static final int BAHAMUT_VIRGIN_SOUL = 21184;
    private static final int BAHAMUT_SHORT = 98629;

    private final Map<Integer, String> titles = new HashMap<>(Map.of(
            AOT_S1, "Shingeki no Kyojin",
            AOT_S2, "Shingeki no Kyojin Season 2",
            AOT_S3, "Shingeki no Kyojin Season 3",
            DEATH_NOTE, "DEATH NOTE",
            FAIRY_TAIL, "FAIRY TAIL",
            SEVEN_DEADLY_SINS, "Nanatsu no Taizai",
            BAHAMUT_GENESIS, "Shingeki no Bahamut: GENESIS",
            BAHAMUT_VIRGIN_SOUL, "Shingeki no Bahamut: VIRGIN SOUL"));

    private static WorkRelation link(int from, int to, String type) {
        return new WorkRelation(from, to, type);
    }

    /** Les oeuvres sont toujours passees de la plus populaire a la moins populaire. */
    private Map<Integer, Integer> group(List<Integer> ids, WorkRelation... relations) {
        return SeriesGrouper.group(ids, List.of(relations), titles);
    }

    @Test
    void les_saisons_d_une_serie_partagent_un_groupe() {
        Map<Integer, Integer> series = group(List.of(AOT_S1, AOT_S2, AOT_S3, DEATH_NOTE),
                link(AOT_S1, AOT_S2, "SEQUEL"),
                link(AOT_S2, AOT_S3, "SEQUEL"));

        assertThat(series.get(AOT_S2)).isEqualTo(AOT_S1);
        assertThat(series.get(AOT_S3)).isEqualTo(AOT_S1);
        assertThat(series.get(DEATH_NOTE)).isEqualTo(DEATH_NOTE);
    }

    @Test
    void le_representant_est_l_oeuvre_la_plus_populaire() {
        // La saison 2 porte la relation, mais c'est la saison 1, plus populaire, qui nomme.
        Map<Integer, Integer> series = group(List.of(AOT_S1, AOT_S2), link(AOT_S2, AOT_S1, "PREQUEL"));

        assertThat(series.values()).containsOnly(AOT_S1);
    }

    @Test
    void le_regroupement_est_transitif_quel_que_soit_l_ordre_des_relations() {
        Map<Integer, Integer> series = group(List.of(AOT_S1, AOT_S2, AOT_S3),
                link(AOT_S3, AOT_S2, "PREQUEL"),
                link(AOT_S2, AOT_S1, "PREQUEL"));

        assertThat(series.values()).containsOnly(AOT_S1);
    }

    @Test
    void une_suite_hors_catalogue_relie_les_saisons_qui_la_citent() {
        // AniList ne relie GENESIS et VIRGIN SOUL que par un court-metrage absent du catalogue.
        Map<Integer, Integer> series = group(List.of(BAHAMUT_GENESIS, BAHAMUT_VIRGIN_SOUL),
                link(BAHAMUT_GENESIS, BAHAMUT_SHORT, "SEQUEL"),
                link(BAHAMUT_VIRGIN_SOUL, BAHAMUT_SHORT, "PREQUEL"));

        assertThat(series.values()).containsOnly(BAHAMUT_GENESIS);
    }

    @Test
    void un_crossover_hors_catalogue_ne_fusionne_pas_deux_franchises() {
        // Cas reel du 21/09/2026 : « FAIRY TAIL & Nanatsu no Taizai Gassaku Manga », spin-off des
        // deux series, faisait de « Fairy Tail » une bonne reponse pour Seven Deadly Sins.
        Map<Integer, Integer> series = group(List.of(FAIRY_TAIL, SEVEN_DEADLY_SINS),
                link(FAIRY_TAIL, CROSSOVER, "SPIN_OFF"),
                link(SEVEN_DEADLY_SINS, CROSSOVER, "SPIN_OFF"));

        assertThat(series.get(FAIRY_TAIL)).isEqualTo(FAIRY_TAIL);
        assertThat(series.get(SEVEN_DEADLY_SINS)).isEqualTo(SEVEN_DEADLY_SINS);
    }

    @Test
    void un_spin_off_hors_catalogue_relie_deux_oeuvres_au_meme_titre() {
        // Re:Zero, Overlord, Pokemon : leurs pieces ne sont reliees que par des side-stories non
        // ingerees. Le titre commun suffit a reconnaitre la meme serie.
        titles.put(1, "Overlord");
        titles.put(2, "Overlord: Fushisha no Oh");
        Map<Integer, Integer> series = group(List.of(1, 2),
                link(1, 999_999, "SPIN_OFF"),
                link(2, 999_999, "SIDE_STORY"));

        assertThat(series.values()).containsOnly(1);
    }

    @Test
    void une_oeuvre_hors_catalogue_n_a_pas_de_serie_a_elle() {
        Map<Integer, Integer> series = group(List.of(AOT_S1), link(AOT_S1, 999_999, "SEQUEL"));

        assertThat(series).containsOnlyKeys(AOT_S1);
    }

    @Test
    void deux_franchises_sans_lien_restent_separees() {
        Map<Integer, Integer> series = group(List.of(AOT_S1, AOT_S2, DEATH_NOTE),
                link(AOT_S1, AOT_S2, "SEQUEL"));

        assertThat(series.get(AOT_S2)).isEqualTo(AOT_S1);
        assertThat(series.get(DEATH_NOTE)).isEqualTo(DEATH_NOTE);
    }

    @Test
    void le_debut_de_titre_se_compare_sur_deux_mots_ou_le_titre_entier() {
        assertThat(SeriesGrouper.sameTitleRoot("Overlord", "Overlord: Fushisha no Oh")).isTrue();
        assertThat(SeriesGrouper.sameTitleRoot("Re:Zero kara Hajimeru", "Re:Zero kara Hajimeru Break Time")).isTrue();
        assertThat(SeriesGrouper.sameTitleRoot("FAIRY TAIL", "Nanatsu no Taizai")).isFalse();
        assertThat(SeriesGrouper.sameTitleRoot("Shingeki no Kyojin", "Shingeki no Bahamut")).isTrue();
        assertThat(SeriesGrouper.sameTitleRoot(null, "Overlord")).isFalse();
    }

    @Test
    void une_adaptation_n_est_pas_une_suite() {
        assertThat(SeriesGrouper.MERGED_RELATION_TYPES)
                .contains("SEQUEL", "PREQUEL", "SIDE_STORY", "ALTERNATIVE")
                .doesNotContain("ADAPTATION", "SOURCE", "CHARACTER", "OTHER");
    }
}
