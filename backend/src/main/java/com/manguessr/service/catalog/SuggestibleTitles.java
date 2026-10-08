package com.manguessr.service.catalog;

import com.manguessr.util.TextNormalizer;

import java.util.Collection;

/**
 * Decide si un synonyme AniList merite une place dans les suggestions d'autocompletion.
 *
 * Le critere n'est <b>pas</b> linguistique : detecter les langues une par une etait une course
 * perdue (l'index manga comptait 4243 synonymes, dont 2377 en thai, coreen, chinois ou
 * cyrillique, et le reste en portugais, polonais, espagnol, allemand...). Le critere est
 * structurel : une traduction etrangere n'est, par construction, ni une <b>sous-chaine</b> du
 * titre romaji ou anglais, ni son <b>acronyme</b>.
 *
 * On exige que le synonyme soit contenu dans le titre de reference, et non l'inverse : sinon
 * « Weak Hero: Un héroe débil » passait, puisqu'il contient « Weak Hero ». Sont ainsi gardes
 * « Demon Slayer », « Kuroko's Basket », « Elfenlied », « AoT », « SnK » ; sont ecartes
 * « Ataque a los Titanes », « Tragones y Mazmorras » et « SAKAMOTO DAYS 坂本日常 ».
 *
 * Ces titres restent <b>acceptes comme reponse</b> : la validation lit la table des titres,
 * pas cet index.
 */
public final class SuggestibleTitles {

    /**
     * En-deca, une sous-chaine ne prouve rien : « go » se retrouve dans quantite de titres.
     * Les abreviations plus courtes passent par la regle d'acronyme.
     */
    private static final int MIN_CONTAINMENT_LENGTH = 4;

    /** Au-dela, ce n'est plus un acronyme : « AoT », « SnK », « FMAB ». */
    private static final int MAX_ACRONYM_LENGTH = 8;

    private static final int MIN_COMPARABLE_LENGTH = 2;

    private SuggestibleTitles() {}

    /** Vrai si {@code synonym} est une variante ou l'acronyme de l'un des titres de reference. */
    public static boolean isVariantOfAny(String synonym, Collection<String> references) {
        String compact = compact(synonym);
        if (compact.length() < MIN_COMPARABLE_LENGTH) {
            return false;
        }

        for (String reference : references) {
            String referenceCompact = compact(reference);
            if (referenceCompact.length() < MIN_COMPARABLE_LENGTH) {
                continue;
            }
            if (compact.length() >= MIN_CONTAINMENT_LENGTH && referenceCompact.contains(compact)) {
                return true;
            }
            if (compact.length() <= MAX_ACRONYM_LENGTH && compact.equals(initialsOf(reference))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forme normalisee sans espaces : « Elfen Lied » et « Elfenlied » donnent la meme cle.
     *
     * Les ecritures non latines traversent {@link TextNormalizer} intactes — c'est voulu, elles
     * ne peuvent alors etre sous-chaine d'aucun titre latin, donc elles tombent d'elles-memes.
     */
    private static String compact(String title) {
        return TextNormalizer.normalize(title).replace(" ", "");
    }

    /** Initiales des mots du titre : « Attack on Titan » donne « aot ». */
    private static String initialsOf(String reference) {
        StringBuilder initials = new StringBuilder();
        for (String word : TextNormalizer.normalize(reference).split(" ")) {
            if (!word.isEmpty()) {
                initials.append(word.charAt(0));
            }
        }
        return initials.toString();
    }
}
