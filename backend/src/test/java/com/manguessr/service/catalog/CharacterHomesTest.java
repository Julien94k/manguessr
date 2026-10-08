package com.manguessr.service.catalog;

import com.manguessr.service.catalog.CharacterHomes.Appearance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oeuvre d'origine d'un personnage. Les cas sont ceux de la base au 30/09/2026 : identifiants
 * AniList reels, annees et popularites arrondies.
 */
class CharacterHomesTest {

    private static final int GYEONG_UN_MOK = 330105;
    private static final int NANO_MACHINE = 120980;
    private static final int MYST_MIGHT_MAYHEM = 175946;

    @Test
    void un_second_role_dans_une_autre_serie_est_une_apparition() {
        // Nano Machine est plus ancien et plus populaire : seul le role designe l'origine.
        List<Appearance> appearances = List.of(
                new Appearance(1, GYEONG_UN_MOK, "SUPPORTING", NANO_MACHINE, 2020, 60_000),
                new Appearance(2, GYEONG_UN_MOK, "MAIN", MYST_MIGHT_MAYHEM, 2023, 15_000));

        assertThat(CharacterHomes.guests(appearances)).containsExactly(1L);
    }

    @Test
    void un_crossover_ou_le_personnage_est_principal_cede_a_la_serie_la_plus_ancienne() {
        // Emilia est MAIN dans Re:Zero comme dans Isekai Quartet, sorti trois ans plus tard.
        List<Appearance> appearances = List.of(
                new Appearance(1, 88572, "MAIN", 21355, 2016, 700_000),
                new Appearance(2, 88572, "MAIN", 21355, 2020, 300_000),
                new Appearance(3, 88572, "MAIN", 104454, 2019, 90_000));

        assertThat(CharacterHomes.guests(appearances)).containsExactly(3L);
    }

    @Test
    void les_saisons_d_une_meme_serie_ne_sont_pas_des_apparitions() {
        // Levi est second role dans certaines saisons, principal dans d'autres : meme serie.
        List<Appearance> appearances = List.of(
                new Appearance(1, 45627, "MAIN", 16498, 2013, 900_000),
                new Appearance(2, 45627, "SUPPORTING", 16498, 2017, 500_000));

        assertThat(CharacterHomes.guests(appearances)).isEmpty();
    }

    @Test
    void sans_role_connu_rien_n_est_marque() {
        // L'annee seule ferait de Nano Machine l'origine de Gyeong-Un Mok.
        List<Appearance> appearances = List.of(
                new Appearance(1, GYEONG_UN_MOK, null, NANO_MACHINE, 2020, 60_000),
                new Appearance(2, GYEONG_UN_MOK, null, MYST_MIGHT_MAYHEM, 2023, 15_000));

        assertThat(CharacterHomes.guests(appearances)).isEmpty();
    }
}
