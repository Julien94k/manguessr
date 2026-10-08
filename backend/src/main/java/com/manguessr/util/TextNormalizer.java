package com.manguessr.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalisation des titres, utilisee a deux endroits qui doivent rester coherents :
 * l'indexation a l'ingestion et la comparaison des reponses en jeu.
 *
 * Objectif : que "Fullmetal Alchemist: Brotherhood", "fullmetal alchemist brotherhood"
 * et "FULLMETAL  ALCHEMIST - BROTHERHOOD" donnent la meme cle.
 *
 * Les caracteres japonais sont conserves tels quels : NFKD ne les decompose pas de facon
 * genante et on veut pouvoir matcher une saisie en kana ou kanji.
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    /**
     * Apostrophes, sous toutes leurs formes typographiques.
     *
     * Elles sont <b>supprimees</b> et non remplacees par une espace : sinon "JoJo's" donnerait
     * "jojo s" et ne correspondrait plus a la graphie "JoJos", pourtant le meme titre.
     */
    private static final Pattern APOSTROPHES = Pattern.compile("['\u2018\u2019\u02BC`\u00B4]");
    /** Le reste de la ponctuation separe des mots : "Steins;Gate" devient "steins gate". */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Articles de tete ignores : "The Promised Neverland" == "Promised Neverland". */
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(the|a|an|le|la|les|un|une)\\s+");

    private TextNormalizer() {}

    /**
     * @return la forme normalisee, ou une chaine vide si l'entree est nulle/vide.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String result = Normalizer.normalize(input, Normalizer.Form.NFKD);
        result = DIACRITICS.matcher(result).replaceAll("");
        result = result.toLowerCase(Locale.ROOT);
        result = APOSTROPHES.matcher(result).replaceAll("");
        result = NON_ALPHANUMERIC.matcher(result).replaceAll(" ");
        result = WHITESPACE.matcher(result).replaceAll(" ").trim();
        result = LEADING_ARTICLE.matcher(result).replaceFirst("");

        return result;
    }

    /**
     * Distance de Levenshtein bornee : des que le cout depasse {@code maxDistance},
     * on arrete et on renvoie {@code maxDistance + 1}.
     *
     * Sert de filet de securite sur les fautes de frappe, l'autocompletion garantissant
     * deja que le joueur choisit un titre existant.
     */
    public static int boundedLevenshtein(String left, String right, int maxDistance) {
        if (left.equals(right)) {
            return 0;
        }
        if (Math.abs(left.length() - right.length()) > maxDistance) {
            return maxDistance + 1;
        }

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMinimum = current[0];

            for (int j = 1; j <= right.length(); j++) {
                int substitution = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitution);
                rowMinimum = Math.min(rowMinimum, current[j]);
            }

            // Toute la ligne depasse deja le seuil : aucune suite ne pourra redescendre.
            if (rowMinimum > maxDistance) {
                return maxDistance + 1;
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }
}
