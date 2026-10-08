package com.manguessr.service.game;

import com.manguessr.config.GameProperties;
import com.manguessr.model.enums.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les baremes sont la transcription des rulebooks AniGuessr : ces tests figent les chiffres
 * exacts, pour qu'une refonte du moteur ne derive pas silencieusement du jeu d'origine.
 */
class ScoreCalculatorTest {

    private ScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        // Valeurs par defaut de GameProperties = celles d'application.yml.
        calculator = new ScoreCalculator(new GameProperties());
    }

    // --- Mode Images : 10 000, -2500 par indice -------------------------------------

    @ParameterizedTest(name = "Images, {0} indice(s) debloque(s) -> {1} points")
    @CsvSource({"0,10000", "1,7500", "2,5000", "3,2500"})
    void images_retire_2500_points_par_indice(int cluesUnlocked, int expected) {
        assertThat(calculator.imagesScore(true, cluesUnlocked)).isEqualTo(expected);
    }

    @Test
    void images_ne_rapporte_rien_si_la_manche_est_ratee() {
        assertThat(calculator.imagesScore(false, 0)).isZero();
    }

    // --- Mode Personnages : 2000/personnage + 500/titre, 4 par manche ----------------

    @Test
    void personnages_cumule_2000_par_personnage_et_500_par_titre() {
        // Manche parfaite : 4 x (2000 + 500) = 10 000, le maximum annonce par le rulebook.
        assertThat(calculator.charactersScore(4, 4)).isEqualTo(10_000);
    }

    @ParameterizedTest(name = "{0} personnages + {1} titres -> {2} points")
    @CsvSource({"0,0,0", "1,0,2000", "0,1,500", "2,1,4500", "4,0,8000", "3,4,8000"})
    void personnages_additionne_les_bonnes_reponses(int characters, int titles, int expected) {
        assertThat(calculator.charactersScore(characters, titles)).isEqualTo(expected);
    }

    // --- Modes Opening / Ending : 5000, -2500 avec le clip video ---------------------

    @Test
    void generique_retire_2500_points_si_le_clip_video_est_utilise() {
        assertThat(calculator.themeScore(true, false)).isEqualTo(5_000);
        assertThat(calculator.themeScore(true, true)).isEqualTo(2_500);
        assertThat(calculator.themeScore(false, false)).isZero();
    }

    // --- Modes Anidle / Mangadle : 10 000, -500 par erreur des la 4e essai -----------

    @ParameterizedTest(name = "Wordle trouve au {0}e essai -> {1} points")
    @CsvSource({
            // Les trois premiers essais sont gratuits : trouver au 4e ne coute encore rien.
            "1,10000", "2,10000", "3,10000", "4,10000",
            // A partir du 5e, chaque essai supplementaire coute 500 points.
            "5,9500", "6,9000", "10,7000",
            // Trouver au 22e essai = 21 mauvaises reponses, dont 18 penalisees
            // (celles posterieures a la 3e) : 10 000 - 18 x 500 = 1000.
            // C'est le score minimum d'une partie gagnee.
            "22,1000",
    })
    void wordle_ne_penalise_qu_a_partir_du_quatrieme_essai(int attemptsUsed, int expected) {
        assertThat(calculator.wordleScore(true, attemptsUsed)).isEqualTo(expected);
    }

    @Test
    void wordle_les_trois_premieres_mauvaises_reponses_sont_gratuites() {
        // Trouver au 4e essai signifie 3 mauvaises reponses, toutes dans la fenetre gratuite.
        assertThat(calculator.wordleScore(true, 4)).isEqualTo(calculator.wordleScore(true, 1));
    }

    @Test
    void wordle_ne_rapporte_rien_si_le_joueur_ne_trouve_pas() {
        assertThat(calculator.wordleScore(false, 22)).isZero();
    }

    @Test
    void wordle_ne_descend_jamais_sous_zero() {
        // Garde-fou si maxAttempts est augmente par configuration sans ajuster le bareme.
        assertThat(calculator.wordleScore(true, 100)).isZero();
    }

    // --- Mode Cover-deblur : 10 000, -1000 par essai rate ----------------------------

    @ParameterizedTest(name = "Cover-deblur trouve au {0}e essai -> {1} points")
    @CsvSource({"1,10000", "2,9000", "5,6000", "10,1000"})
    void cover_reveal_retire_1000_points_par_essai_rate(int attemptsUsed, int expected) {
        assertThat(calculator.coverRevealScore(true, attemptsUsed)).isEqualTo(expected);
    }

    @Test
    void cover_reveal_ne_rapporte_rien_apres_dix_echecs() {
        assertThat(calculator.coverRevealScore(false, 10)).isZero();
    }

    // --- Valeurs affichees avant de jouer --------------------------------------------

    @Test
    void le_score_maximum_annonce_correspond_au_rulebook() {
        assertThat(calculator.maxScoreFor(GameMode.IMAGES)).isEqualTo(10_000);
        assertThat(calculator.maxScoreFor(GameMode.CHARACTERS)).isEqualTo(10_000);
        assertThat(calculator.maxScoreFor(GameMode.OPENING)).isEqualTo(5_000);
        assertThat(calculator.maxScoreFor(GameMode.ENDING)).isEqualTo(5_000);
        assertThat(calculator.maxScoreFor(GameMode.WORDLE)).isEqualTo(10_000);
        assertThat(calculator.maxScoreFor(GameMode.COVER_REVEAL)).isEqualTo(10_000);
    }

    @Test
    void seuls_les_modes_de_deduction_autorisent_plusieurs_essais() {
        assertThat(calculator.maxAttemptsFor(GameMode.WORDLE)).isEqualTo(22);
        assertThat(calculator.maxAttemptsFor(GameMode.COVER_REVEAL)).isEqualTo(10);
        assertThat(calculator.maxAttemptsFor(GameMode.IMAGES)).isEqualTo(1);
        assertThat(calculator.maxAttemptsFor(GameMode.OPENING)).isEqualTo(1);
    }
}
