package com.manguessr.service.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Score de notoriete : ce qui decide du palier de difficulte d'une oeuvre.
 *
 * Les chiffres viennent du catalogue reel (19/09/2026). Les cas retenus sont ceux qui ont
 * motive la formule : la popularite AniList seule classait Evangelion et Parasyte derriere
 * des webtoons de 2022, et une ponderation trop forte de l'adaptation faisait sortir du
 * palier facile les mangas sans anime.
 */
class RecognitionScoreTest {

    private double audience(int popularity, int favourites, int adaptation) {
        return RecognitionScore.audience(popularity, favourites, adaptation);
    }

    @Test
    void une_oeuvre_connue_par_son_anime_passe_devant_un_webtoon_recent() {
        // Neon Genesis Evangelion : peu lu en manga, mais l'anime est vu de tous.
        double evangelion = audience(38_880, 2_216, 495_456);
        // « The Swordmaster's Son », webtoon coreen de 2022 : rang 150 par la popularite seule.
        double webtoon = audience(43_656, 2_065, 0);

        assertThat(evangelion).isGreaterThan(webtoon);
    }

    @Test
    void un_manga_sans_anime_reste_devant_une_adaptation_moyenne() {
        // Le piege de la premiere version : un poids trop fort sur l'adaptation faisait
        // degringoler en bloc les mangas sans anime — Vagabond, Punpun, 20th Century Boys.
        double vagabond = audience(52_000, 5_600, 0);
        double adaptationMoyenne = audience(20_000, 800, 200_000);

        assertThat(vagabond).isGreaterThan(adaptationMoyenne);
    }

    @Test
    void les_favoris_departagent_deux_oeuvres_aussi_suivies() {
        // ARIA (1601 favoris) contre The New Gate (369), a popularite quasi identique : la
        // premiere est un classique, la seconde une simple ligne de liste.
        assertThat(audience(18_251, 1_601, 0)).isGreaterThan(audience(18_280, 369, 0));
    }

    @Test
    void une_donnee_absente_ne_fait_pas_echouer_le_calcul() {
        // AniList laisse regulierement ces compteurs a null sur une oeuvre recente.
        assertThat(RecognitionScore.audience(null, null, null)).isZero();
        assertThat(RecognitionScore.audience(1_000, null, null)).isEqualTo(1_000);
        assertThat(RecognitionScore.audience(null, 10, null)).isEqualTo(100);
    }
}
