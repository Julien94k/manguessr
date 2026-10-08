package com.manguessr.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Titre montre au joueur, et appartenance a une serie. */
class MediaWorkTest {

    private MediaWork work(String english, String romaji, String nativeTitle) {
        MediaWork work = new MediaWork();
        work.setTitleEnglish(english);
        work.setTitleRomaji(romaji);
        work.setTitleNative(nativeTitle);
        return work;
    }

    @Test
    void le_titre_anglais_est_prefere() {
        assertThat(work("Attack on Titan", "Shingeki no Kyojin", "進撃の巨人").displayTitle())
                .isEqualTo("Attack on Titan");
    }

    @Test
    void sans_titre_anglais_le_romaji_prend_le_relais() {
        assertThat(work(null, "Oyasumi Punpun", "おやすみプンプン").displayTitle()).isEqualTo("Oyasumi Punpun");
        assertThat(work("  ", "Oyasumi Punpun", null).displayTitle()).isEqualTo("Oyasumi Punpun");
    }

    @Test
    void en_dernier_recours_le_titre_natif() {
        assertThat(work(null, null, "進撃の巨人").displayTitle()).isEqualTo("進撃の巨人");
    }

    private MediaWork member(int anilistId, Integer seriesId) {
        MediaWork work = new MediaWork();
        work.setAnilistId(anilistId);
        work.setSeriesId(seriesId);
        return work;
    }

    @Test
    void deux_saisons_d_une_meme_serie_se_reconnaissent() {
        MediaWork season1 = member(16498, 16498);
        MediaWork season2 = member(25777, 16498);

        assertThat(season1.sameSeriesAs(season2)).isTrue();
        assertThat(season2.sameSeriesAs(season1)).isTrue();
        assertThat(season2.seriesKey()).isEqualTo(16498);
    }

    @Test
    void une_oeuvre_sans_serie_est_la_sienne() {
        MediaWork alone = member(1535, null);

        assertThat(alone.seriesKey()).isEqualTo(1535);
        assertThat(alone.sameSeriesAs(member(16498, 16498))).isFalse();
        assertThat(alone.sameSeriesAs(alone)).isTrue();
    }

    @Test
    void sans_aucun_identifiant_deux_oeuvres_ne_sont_jamais_de_la_meme_serie() {
        // Sinon deux fiches incompletes vaudraient reponse l'une pour l'autre.
        assertThat(member(0, null).sameSeriesAs(new MediaWork())).isFalse();
        assertThat(new MediaWork().sameSeriesAs(new MediaWork())).isFalse();
    }
}
