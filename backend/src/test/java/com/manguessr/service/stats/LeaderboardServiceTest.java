package com.manguessr.service.stats;

import com.manguessr.model.dto.LeaderboardEntryView;
import com.manguessr.repository.projection.ScoreAggregate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardServiceTest {

    @Test
    void les_ex_aequo_partagent_leur_rang_et_le_suivant_saute() {
        List<LeaderboardEntryView> ranked = LeaderboardService.rank(List.of(
                new ScoreAggregate(1L, "alice", 30_000L, 3L),
                new ScoreAggregate(2L, "bob", 20_000L, 2L),
                new ScoreAggregate(3L, "chloe", 20_000L, 3L),
                new ScoreAggregate(4L, "dan", 5_000L, 1L)));

        assertThat(ranked).extracting(LeaderboardEntryView::rank).containsExactly(1, 2, 2, 4);
        assertThat(ranked).extracting(LeaderboardEntryView::username)
                .containsExactly("alice", "bob", "chloe", "dan");
    }

    @Test
    void un_classement_vide_reste_vide() {
        assertThat(LeaderboardService.rank(List.of())).isEmpty();
    }
}
