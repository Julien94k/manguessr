package com.manguessr.service.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les en-tetes de telechargement different selon le CDN, et une erreur ici ne leve rien :
 * MangaDex repond 200 avec une image de remplacement. Seul ce test la detecte.
 */
class MediaProxyServiceTest {

    @Test
    void les_hotes_anilist_recoivent_le_referer_qu_ils_exigent() {
        assertThat(MediaProxyService.refererFor("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1.jpg"))
                .contains("https://anilist.co/");
        assertThat(MediaProxyService.refererFor("https://anilist.co/img/x.jpg"))
                .contains("https://anilist.co/");
    }

    @Test
    void mangadex_ne_recoit_aucun_referer_sinon_il_sert_une_image_de_remplacement() {
        assertThat(MediaProxyService.refererFor(
                "https://uploads.mangadex.org/covers/a77742b1/fc3df9c7.jpg.512.jpg")).isEmpty();
    }

    @Test
    void un_hote_qui_imite_anilist_n_est_pas_confondu() {
        assertThat(MediaProxyService.refererFor("https://notanilist.co/x.jpg")).isEmpty();
        assertThat(MediaProxyService.refererFor("https://anilist.co.evil.example/x.jpg")).isEmpty();
    }
}
