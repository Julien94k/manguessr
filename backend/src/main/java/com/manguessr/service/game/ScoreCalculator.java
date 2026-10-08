package com.manguessr.service.game;

import com.manguessr.config.GameProperties;
import com.manguessr.model.enums.GameMode;
import org.springframework.stereotype.Service;

/**
 * Calcul des scores, seule autorite en la matiere.
 *
 * Le client ne transmet jamais de score : il est toujours recalcule ici a partir des
 * essais enregistres en base. Les baremes viennent de {@link GameProperties}, lui-meme
 * aligne sur les rulebooks AniGuessr.
 *
 * Tous les resultats sont bornes a zero : une manche ratee ne retire jamais de points
 * a la manche suivante.
 */
@Service
public class ScoreCalculator {

    private final GameProperties properties;

    public ScoreCalculator(GameProperties properties) {
        this.properties = properties;
    }

    /**
     * Modes Images : 10 000 points, moins 2500 par indice debloque.
     *
     * La premiere image est offerte et ne compte pas comme un indice : seuls les indices
     * effectivement debloques par le joueur sont factures.
     *
     * @param cluesUnlocked nombre d'indices payants debloques (0 a 3)
     */
    public int imagesScore(boolean solved, int cluesUnlocked) {
        if (!solved) {
            return 0;
        }
        GameProperties.Images config = properties.getImages();
        return Math.max(0, config.getMaxScore() - cluesUnlocked * config.getClueCost());
    }

    /**
     * Mode Personnages : 2000 points par personnage trouve, 500 par titre trouve.
     *
     * Il n'y a pas de notion de « manche reussie » : le score est la somme des bonnes
     * reponses, et une manche laissee vide rapporte simplement zero.
     */
    public int charactersScore(int correctCharacters, int correctTitles) {
        GameProperties.Characters config = properties.getCharacters();
        return Math.max(0, correctCharacters * config.getCharacterPoints()
                + correctTitles * config.getTitlePoints());
    }

    /**
     * Modes Opening et Ending : 5000 points, moins 2500 si le clip video a ete debloque.
     */
    public int themeScore(boolean solved, boolean videoClueUsed) {
        if (!solved) {
            return 0;
        }
        GameProperties.Theme config = properties.getTheme();
        return Math.max(0, config.getMaxScore() - (videoClueUsed ? config.getVideoClueCost() : 0));
    }

    /**
     * Modes Anidle et Mangadle : 10 000 points, moins 500 par mauvaise reponse
     * <b>a partir de la quatrieme</b>.
     *
     * Les trois premiers essais servent a sonder les attributs sans penalite : c'est ce qui
     * rend le mode jouable, puisqu'on ne peut rien deduire avant d'avoir propose quelque chose.
     * Un joueur trouvant au 22e essai conserve 500 points.
     *
     * @param attemptsUsed nombre total d'essais consommes, bonne reponse comprise
     */
    public int wordleScore(boolean solved, int attemptsUsed) {
        if (!solved) {
            return 0;
        }
        GameProperties.Wordle config = properties.getWordle();
        int penalizedAttempts = Math.max(0, attemptsUsed - 1 - config.getFreeAttempts());
        return Math.max(0, config.getMaxScore() - penalizedAttempts * config.getWrongAnswerCost());
    }

    /**
     * Mode Cover-deblur : 10 000 points, moins 1000 par essai rate.
     *
     * @param attemptsUsed nombre total d'essais consommes, bonne reponse comprise
     */
    public int coverRevealScore(boolean solved, int attemptsUsed) {
        if (!solved) {
            return 0;
        }
        GameProperties.CoverReveal config = properties.getCoverReveal();
        int wrongAttempts = Math.max(0, attemptsUsed - 1);
        return Math.max(0, config.getMaxScore() - wrongAttempts * config.getWrongAnswerCost());
    }

    /** Score maximum atteignable sur une manche, pour l'affichage avant de jouer. */
    public int maxScoreFor(GameMode mode) {
        return switch (mode) {
            case IMAGES -> properties.getImages().getMaxScore();
            case CHARACTERS -> {
                GameProperties.Characters config = properties.getCharacters();
                yield config.getPerRound() * (config.getCharacterPoints() + config.getTitlePoints());
            }
            case OPENING, ENDING -> properties.getTheme().getMaxScore();
            case WORDLE -> properties.getWordle().getMaxScore();
            case COVER_REVEAL -> properties.getCoverReveal().getMaxScore();
        };
    }

    /** Nombre d'essais autorises sur une manche. Les modes a reponse unique en ont un seul. */
    public int maxAttemptsFor(GameMode mode) {
        return switch (mode) {
            case WORDLE -> properties.getWordle().getMaxAttempts();
            case COVER_REVEAL -> properties.getCoverReveal().getMaxAttempts();
            case IMAGES, CHARACTERS, OPENING, ENDING -> 1;
        };
    }
}
