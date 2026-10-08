package com.manguessr.service.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    @Test
    void aucune_partie_donne_une_serie_nulle() {
        assertThat(StreakCalculator.current(List.of(), TODAY)).isZero();
        assertThat(StreakCalculator.best(List.of())).isZero();
    }

    @Test
    void les_jours_consecutifs_jusqu_a_aujourd_hui_forment_la_serie() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
        assertThat(StreakCalculator.current(days, TODAY)).isEqualTo(3);
    }

    @Test
    void la_serie_survit_tant_que_la_journee_n_est_pas_finie() {
        // Joue hier et avant-hier, pas encore aujourd'hui : la serie tient toujours.
        List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2));
        assertThat(StreakCalculator.current(days, TODAY)).isEqualTo(2);
    }

    @Test
    void un_jour_manque_casse_la_serie() {
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));
        assertThat(StreakCalculator.current(days, TODAY)).isZero();
    }

    @Test
    void la_meilleure_serie_ignore_doublons_et_desordre() {
        List<LocalDate> days = List.of(
                TODAY, TODAY.minusDays(10), TODAY.minusDays(9), TODAY.minusDays(9),
                TODAY.minusDays(8), TODAY.minusDays(1), TODAY.minusDays(7));
        assertThat(StreakCalculator.best(days)).isEqualTo(4);
    }
}
