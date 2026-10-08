package com.manguessr.service.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DifficultyTiersTest {

    private static List<Long> ranks(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    @Test
    void agrandir_le_catalogue_ne_change_pas_le_palier_des_oeuvres_populaires() {
        DifficultyTiers small = DifficultyTiers.byRank(ranks(1000), 150, 500, 2000);
        DifficultyTiers large = DifficultyTiers.byRank(ranks(4000), 150, 500, 2000);

        assertThat(large.easy()).isEqualTo(small.easy()).hasSize(150).endsWith(150L);
        assertThat(large.medium()).isEqualTo(small.medium()).hasSize(350).endsWith(500L);
        assertThat(small.hard()).hasSize(500);
        assertThat(large.hard()).hasSize(1500).endsWith(2000L);
    }

    @Test
    void les_oeuvres_au_dela_du_rang_plafond_ne_recoivent_aucun_palier() {
        DifficultyTiers tiers = DifficultyTiers.byRank(ranks(4000), 150, 500, 2000);

        assertThat(tiers.unranked()).hasSize(2000).startsWith(2001L).endsWith(4000L);
    }

    @Test
    void un_petit_catalogue_remplit_les_paliers_dans_l_ordre() {
        DifficultyTiers tiers = DifficultyTiers.byRank(ranks(300), 150, 500, 2000);

        assertThat(tiers.easy()).hasSize(150);
        assertThat(tiers.medium()).hasSize(150);
        assertThat(tiers.hard()).isEmpty();
        assertThat(tiers.unranked()).isEmpty();
    }

    @Test
    void des_rangs_non_croissants_sont_refuses() {
        assertThatThrownBy(() -> DifficultyTiers.byRank(ranks(10), 500, 150, 2000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
