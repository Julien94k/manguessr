package com.manguessr.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La normalisation est le point de contact entre l'index construit a l'ingestion et la
 * validation des reponses en jeu : une divergence ici rendrait des bonnes reponses invalides.
 */
class TextNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" et \"{1}\" doivent se normaliser pareil")
    @CsvSource({
            // Casse et espaces surnumeraires
            "'Fullmetal Alchemist','FULLMETAL   ALCHEMIST'",
            // Ponctuation : deux-points, tirets, apostrophes
            "'Fullmetal Alchemist: Brotherhood','Fullmetal Alchemist - Brotherhood'",
            "'JoJos Bizarre Adventure',\"JoJo's Bizarre Adventure\"",
            // Accents
            "'Pokemon','Pokémon'",
            // Article de tete ignore
            "'The Promised Neverland','Promised Neverland'",
            // Ponctuation de fin
            "'Steins;Gate','Steins Gate'",
            "'K-On!','K On'",
            // Apostrophe typographique (U+2019), courante dans les titres anglais d'AniList
            "\"JoJo's Bizarre Adventure\",'JoJo\u2019s Bizarre Adventure'",
    })
    void des_ecritures_differentes_du_meme_titre_donnent_la_meme_cle(String left, String right) {
        assertThat(TextNormalizer.normalize(left))
                .isEqualTo(TextNormalizer.normalize(right));
    }

    @Test
    void des_titres_differents_ne_sont_pas_confondus() {
        assertThat(TextNormalizer.normalize("Naruto"))
                .isNotEqualTo(TextNormalizer.normalize("Naruto Shippuden"));
    }

    @Test
    void le_japonais_est_conserve_pour_rester_saisissable() {
        assertThat(TextNormalizer.normalize("進撃の巨人")).isEqualTo("進撃の巨人");
    }

    @Test
    void une_entree_vide_ou_nulle_donne_une_chaine_vide() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
        // Un synonyme purement symbolique devient inutilisable : l'ingestion doit l'ecarter.
        assertThat(TextNormalizer.normalize("!!!")).isEmpty();
    }

    @Test
    void les_chiffres_sont_preserves() {
        assertThat(TextNormalizer.normalize("Re:Zero kara Hajimeru Isekai Seikatsu 2nd Season"))
                .isEqualTo("re zero kara hajimeru isekai seikatsu 2nd season");
    }

    @Test
    void la_distance_de_levenshtein_tolere_une_faute_de_frappe() {
        String reference = TextNormalizer.normalize("Bleach");

        assertThat(TextNormalizer.boundedLevenshtein(reference, TextNormalizer.normalize("Bleech"), 1))
                .isEqualTo(1);
        assertThat(TextNormalizer.boundedLevenshtein(reference, reference, 1))
                .isZero();
    }

    @Test
    void la_distance_bornee_rejette_ce_qui_depasse_le_seuil() {
        int distance = TextNormalizer.boundedLevenshtein(
                TextNormalizer.normalize("Naruto"),
                TextNormalizer.normalize("One Piece"), 1);

        // Au-dela du seuil on renvoie maxDistance + 1 sans calculer la vraie distance.
        assertThat(distance).isEqualTo(2);
    }

    @Test
    void la_distance_bornee_coupe_court_sur_des_longueurs_tres_differentes() {
        assertThat(TextNormalizer.boundedLevenshtein("a", "abcdefghij", 2)).isEqualTo(3);
    }
}
