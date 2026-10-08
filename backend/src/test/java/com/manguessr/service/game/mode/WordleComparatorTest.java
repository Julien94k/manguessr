package com.manguessr.service.game.mode;

import com.manguessr.model.dto.WordleCellView;
import com.manguessr.model.dto.WordleRowView;
import com.manguessr.model.entity.MediaCredit;
import com.manguessr.model.entity.MediaTag;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.CreditKind;
import com.manguessr.model.enums.WorkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La table de comparaison est le seul canal d'information du mode Wordle : une couleur
 * fausse envoie le joueur dans une mauvaise direction pendant 22 essais.
 */
class WordleComparatorTest {

    private WordleComparator comparator;
    private MediaWork target;

    @BeforeEach
    void setUp() {
        comparator = new WordleComparator();

        target = work(1L, "Cible", 2015, 85, "MANGA");
        target.getGenres().addAll(List.of("Action", "Drama"));
        target.addCredit(new MediaCredit("Studio Cible", CreditKind.STUDIO, null));
        // Rang eleve = tag representatif (vert) ; rang bas = tag secondaire (jaune).
        target.addTag(new MediaTag("Tragedy", 90, "Theme", false));
        target.addTag(new MediaTag("Military", 30, "Theme", false));
    }

    private MediaWork work(Long id, String title, Integer year, Integer score, String source) {
        MediaWork work = new MediaWork();
        work.setId(id);
        work.setType(WorkType.ANIME);
        work.setTitleRomaji(title);
        work.setYear(year);
        work.setAverageScore(score);
        work.setSource(source);
        return work;
    }

    private WordleCellView findTag(WordleRowView row, String name) {
        return row.tags().stream()
                .filter(cell -> cell.value().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tag absent de la ligne : " + name));
    }

    @Test
    void une_annee_plus_recente_chez_la_cible_pointe_vers_le_haut() {
        MediaWork guess = work(2L, "Plus ancien", 2005, 85, "MANGA");

        WordleRowView row = comparator.compare(guess, target);

        assertThat(row.year().value()).isEqualTo("2005");
        assertThat(row.year().status()).isEqualTo("MISS");
        // La cible est en 2015 : le joueur doit chercher plus recent.
        assertThat(row.year().compare()).isEqualTo("UP");
    }

    @Test
    void une_annee_plus_ancienne_chez_la_cible_pointe_vers_le_bas() {
        WordleRowView row = comparator.compare(work(2L, "Plus recent", 2022, 85, "MANGA"), target);

        assertThat(row.year().compare()).isEqualTo("DOWN");
    }

    @Test
    void une_valeur_identique_est_verte_et_sans_fleche() {
        WordleRowView row = comparator.compare(work(2L, "Meme annee", 2015, 85, "MANGA"), target);

        assertThat(row.year().status()).isEqualTo("MATCH");
        assertThat(row.year().compare()).isNull();
        assertThat(row.score().status()).isEqualTo("MATCH");
        assertThat(row.source().status()).isEqualTo("MATCH");
    }

    @Test
    void un_tag_representatif_chez_la_cible_est_vert() {
        MediaWork guess = work(2L, "Proposition", 2015, 85, "MANGA");
        guess.addTag(new MediaTag("Tragedy", 80, "Theme", false));

        assertThat(findTag(comparator.compare(guess, target), "Tragedy").status())
                .isEqualTo("MATCH");
    }

    @Test
    void un_tag_commun_mais_secondaire_chez_la_cible_est_jaune() {
        MediaWork guess = work(2L, "Proposition", 2015, 85, "MANGA");
        guess.addTag(new MediaTag("Military", 95, "Theme", false));

        // Le tag est majeur chez la proposition mais mineur chez la cible : bonne piste,
        // mais ce n'est pas ce qui definit l'oeuvre cherchee.
        assertThat(findTag(comparator.compare(guess, target), "Military").status())
                .isEqualTo("PARTIAL");
    }

    @Test
    void un_tag_absent_de_la_cible_est_rouge() {
        MediaWork guess = work(2L, "Proposition", 2015, 85, "MANGA");
        guess.addTag(new MediaTag("Mecha", 90, "Theme", false));

        assertThat(findTag(comparator.compare(guess, target), "Mecha").status()).isEqualTo("MISS");
    }

    @Test
    void les_tags_spoilers_de_la_cible_ne_sont_jamais_reveles() {
        target.addTag(new MediaTag("Twist Final", 99, "Theme", true));

        MediaWork guess = work(2L, "Proposition", 2015, 85, "MANGA");
        guess.addTag(new MediaTag("Twist Final", 99, "Theme", false));

        // Le tag est marque spoiler chez la cible : il ne doit pas confirmer la piste.
        assertThat(findTag(comparator.compare(guess, target), "Twist Final").status())
                .isEqualTo("MISS");
    }

    @Test
    void les_genres_communs_ressortent_en_vert_et_les_autres_en_rouge() {
        MediaWork guess = work(2L, "Proposition", 2015, 85, "MANGA");
        guess.getGenres().addAll(List.of("Action", "Comedy"));

        WordleRowView row = comparator.compare(guess, target);

        assertThat(row.genres()).extracting(WordleCellView::value)
                .containsExactly("Action", "Comedy");
        assertThat(row.genres().get(0).status()).isEqualTo("MATCH");
        assertThat(row.genres().get(1).status()).isEqualTo("MISS");
    }

    @Test
    void la_bonne_reponse_est_signalee_comme_telle() {
        assertThat(comparator.compare(target, target).correct()).isTrue();
    }

    @Test
    void une_donnee_manquante_ne_fait_pas_echouer_la_comparaison() {
        MediaWork incomplete = work(2L, "Incomplet", null, null, null);

        WordleRowView row = comparator.compare(incomplete, target);

        assertThat(row.year().value()).isEqualTo("?");
        assertThat(row.score().value()).isEqualTo("?");
        assertThat(row.credit()).isNotEmpty();
        assertThat(row.genres()).isNotEmpty();
    }

    @Test
    void la_source_est_rendue_lisible() {
        MediaWork guess = work(2L, "Proposition", 2015, 85, "LIGHT_NOVEL");

        assertThat(comparator.compare(guess, target).source().value()).isEqualTo("Light novel");
    }
}
