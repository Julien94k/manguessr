package com.manguessr.service.game.mode;

/**
 * Resultat d'une tentative, avant persistance.
 *
 * @param correct     la manche est resolue
 * @param exhausted   les essais sont epuises : la manche se termine en echec
 * @param score       points acquis sur la manche
 * @param message     precision affichable, par exemple un doublon signale
 * @param guessedWork oeuvre proposee, quand la saisie a pu etre resolue
 */
public record GuessOutcome(
        boolean correct,
        boolean exhausted,
        int score,
        String message,
        com.manguessr.model.entity.MediaWork guessedWork) {

    public static GuessOutcome solved(int score) {
        return new GuessOutcome(true, false, score, null, null);
    }

    public static GuessOutcome wrong(boolean exhausted, int score) {
        return new GuessOutcome(false, exhausted, score, null, null);
    }

    /** Tentative sans effet : ne consomme pas d'essai. */
    public static GuessOutcome rejected(String message) {
        return new GuessOutcome(false, false, -1, message, null);
    }

    public boolean isRejected() {
        return score < 0;
    }

    public GuessOutcome withGuessedWork(com.manguessr.model.entity.MediaWork work) {
        return new GuessOutcome(correct, exhausted, score, message, work);
    }
}
