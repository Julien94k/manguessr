package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.entity.GameSession;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.RoundAttempt;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Jugement d'une proposition du Mangadle.
 *
 * Le mode ne compare pas deux oeuvres mais deux <b>series</b> : repondre « Attack on Titan »
 * quand la cible est la saison 2 doit gagner, et proposer ensuite une autre saison n'apporte
 * aucune information nouvelle.
 */
class WordleModeHandlerTest {

    private static final int AOT_SERIES = 16498;

    private AnswerMatcher answerMatcher;
    private WordleModeHandler handler;
    private SessionRound round;

    @BeforeEach
    void setUp() {
        answerMatcher = mock(AnswerMatcher.class);
        ScoreCalculator scoreCalculator = mock(ScoreCalculator.class);
        when(scoreCalculator.wordleScore(anyBoolean(), anyInt())).thenReturn(10_000);

        handler = new WordleModeHandler(mock(MediaWorkRepository.class), answerMatcher, scoreCalculator,
                mock(WordleComparator.class), new GameProperties(), mock(RoundSupport.class));

        GameSession session = new GameSession();
        session.setTheme(WorkType.ANIME);
        round = new SessionRound();
        round.setSession(session);
        round.setWork(work(25777, AOT_SERIES, "Attack on Titan Season 2"));
    }

    private MediaWork work(int anilistId, Integer seriesId, String title) {
        MediaWork work = new MediaWork();
        work.setAnilistId(anilistId);
        work.setSeriesId(seriesId);
        work.setTitleEnglish(title);
        return work;
    }

    private GuessOutcome guess(String answer, MediaWork resolved) {
        when(answerMatcher.resolveWork(answer, WorkType.ANIME)).thenReturn(resolved);
        return handler.evaluate(round, new GuessRequest(0, answer, null));
    }

    @Test
    void repondre_le_titre_de_la_serie_gagne_la_manche() {
        GuessOutcome outcome = guess("Attack on Titan", work(16498, AOT_SERIES, "Attack on Titan"));

        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.score()).isEqualTo(10_000);
    }

    @Test
    void une_autre_serie_reste_une_mauvaise_reponse() {
        GuessOutcome outcome = guess("Death Note", work(1535, 1535, "Death Note"));

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.isRejected()).isFalse();
    }

    @Test
    void une_autre_saison_d_une_serie_deja_proposee_ne_coute_pas_d_essai() {
        // Les deux lignes du tableau seraient identiques : c'est un doublon, pas un essai.
        MediaWork fateZero = work(10087, 10087, "Fate/Zero");
        round.addAttempt(new RoundAttempt(1, "Fate/Zero", false, fateZero));

        GuessOutcome outcome = guess("Fate/Zero 2nd Season", work(11741, 10087, "Fate/Zero 2nd Season"));

        assertThat(outcome.isRejected()).isTrue();
        assertThat(outcome.message()).contains("Fate/Zero");
    }

    @Test
    void sauter_a_l_indice_consomme_les_essais_jusqu_a_la_couverture() {
        round.setAttemptsUsed(3);

        handler.unlockClue(round);

        assertThat(round.getAttemptsUsed()).isEqualTo(12);
    }

    @Test
    void apres_la_couverture_le_saut_mene_au_synopsis() {
        round.setAttemptsUsed(14);

        handler.unlockClue(round);

        assertThat(round.getAttemptsUsed()).isEqualTo(18);
    }

    @Test
    void le_saut_est_refuse_avant_trois_essais() {
        round.setAttemptsUsed(2);

        assertThatThrownBy(() -> handler.unlockClue(round)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void le_dernier_saut_laisse_un_essai_pour_repondre() {
        round.setAttemptsUsed(18);

        handler.unlockClue(round);

        assertThat(round.getAttemptsUsed()).isEqualTo(21);
        assertThat(round.getAttemptsUsed()).isLessThan(new GameProperties().getWordle().getMaxAttempts());
    }

    @Test
    void plus_de_saut_une_fois_le_personnage_debloque() {
        round.setAttemptsUsed(21);

        assertThatThrownBy(() -> handler.unlockClue(round)).isInstanceOf(IllegalStateException.class);
        assertThat(round.getAttemptsUsed()).isEqualTo(21);
    }
}
