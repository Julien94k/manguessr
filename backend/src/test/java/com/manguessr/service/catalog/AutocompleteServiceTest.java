package com.manguessr.service.catalog;

import com.manguessr.model.dto.AutocompleteEntry;
import com.manguessr.model.enums.TitleKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaTitleRepository;
import com.manguessr.repository.projection.TitleEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le champ de reponse n'affiche que cinq suggestions. Depuis qu'une serie entiere vaut une
 * meme reponse, elle n'a droit qu'a une entree : « Attack on Titan » et ses cinq saisons
 * remplissaient la liste a elles seules. Les titres ecartes restent trouvables en alias.
 */
class AutocompleteServiceTest {

    private static final int AOT = 16498;
    private static final int NARUTO = 20;
    private static final int JOJO = 30105;

    private List<AutocompleteEntry> titles(TitleEntry... entries) {
        MediaTitleRepository titleRepository = mock(MediaTitleRepository.class);
        when(titleRepository.findAllEntriesByType(WorkType.ANIME)).thenReturn(List.of(entries));
        return new AutocompleteService(titleRepository, mock(MediaCharacterRepository.class))
                .titles(WorkType.ANIME);
    }

    private TitleEntry entry(long workId, int anilistId, Integer seriesId, String value, TitleKind kind) {
        return new TitleEntry(workId, anilistId, seriesId, value, kind);
    }

    @Test
    void une_serie_n_occupe_qu_une_suggestion() {
        List<AutocompleteEntry> titles = titles(
                entry(1, AOT, AOT, "Shingeki no Kyojin", TitleKind.ROMAJI),
                entry(1, AOT, AOT, "Attack on Titan", TitleKind.ENGLISH),
                entry(2, 25777, AOT, "Shingeki no Kyojin Season 2", TitleKind.ROMAJI),
                entry(2, 25777, AOT, "Attack on Titan Season 2", TitleKind.ENGLISH),
                entry(3, 99147, AOT, "Shingeki no Kyojin: The Final Season", TitleKind.ROMAJI));

        // Le libelle vient de l'oeuvre qui nomme la serie, le plus court de ses titres.
        assertThat(titles).singleElement()
                .extracting(AutocompleteEntry::label).isEqualTo("Attack on Titan");
        assertThat(titles.get(0).aliases()).contains(
                "Shingeki no Kyojin", "Shingeki no Kyojin Season 2", "Attack on Titan Season 2");
    }

    @Test
    void les_titres_ecartes_restent_trouvables_en_alias() {
        // Sans alias, « Boruto » deviendrait introuvable au clavier.
        List<AutocompleteEntry> titles = titles(
                entry(1, NARUTO, NARUTO, "NARUTO", TitleKind.ROMAJI),
                entry(2, 1735, NARUTO, "NARUTO: Shippuuden", TitleKind.ROMAJI),
                entry(3, 34566, NARUTO, "BORUTO: NARUTO NEXT GENERATIONS", TitleKind.ROMAJI));

        assertThat(titles).singleElement().satisfies(entry -> {
            assertThat(entry.label()).isEqualTo("NARUTO");
            assertThat(entry.aliases())
                    .containsExactly("BORUTO: NARUTO NEXT GENERATIONS", "NARUTO: Shippuuden");
        });
    }

    @Test
    void une_serie_sans_titre_generique_prend_celui_de_son_oeuvre_de_reference() {
        // JoJo : aucune partie n'en prolonge une autre et il n'existe pas de fiche « JoJo no
        // Kimyou na Bouken » seule. C'est la partie la plus populaire qui nomme la suggestion,
        // sous son titre anglais.
        List<AutocompleteEntry> titles = titles(
                entry(1, JOJO, JOJO, "JoJo no Kimyou na Bouken: Steel Ball Run", TitleKind.ROMAJI),
                entry(1, JOJO, JOJO, "JoJo's Bizarre Adventure: Part 7–Steel Ball Run", TitleKind.ENGLISH),
                entry(2, 30104, JOJO, "JoJo no Kimyou na Bouken: Stone Ocean", TitleKind.ROMAJI),
                entry(3, 30103, JOJO, "JoJo no Kimyou na Bouken: Phantom Blood", TitleKind.ROMAJI));

        assertThat(titles).singleElement()
                .extracting(AutocompleteEntry::label)
                .isEqualTo("JoJo's Bizarre Adventure: Part 7–Steel Ball Run");
    }

    @Test
    void le_libelle_est_le_titre_dont_les_autres_derivent() {
        // « Fullmetal Alchemist: Brotherhood » est l'oeuvre la plus populaire de la serie, mais
        // le titre generique est celui de 2003, que le sien prolonge.
        List<AutocompleteEntry> titles = titles(
                entry(1, 5114, 5114, "Fullmetal Alchemist: Brotherhood", TitleKind.ENGLISH),
                entry(2, 121, 5114, "Fullmetal Alchemist", TitleKind.ENGLISH));
        // Le titre de l'oeuvre la plus populaire prolonge celui de 2003 : c'est lui le generique.

        assertThat(titles).singleElement()
                .extracting(AutocompleteEntry::label).isEqualTo("Fullmetal Alchemist");
    }

    @Test
    void un_titre_qui_a_sa_propre_suite_ne_prend_pas_le_dessus_sur_l_oeuvre_de_reference() {
        // « Owarimonogatari » est prolonge par sa seconde saison, mais la serie s'appelle
        // « Bakemonogatari » : seul un titre que l'oeuvre de reference prolonge est generique.
        List<AutocompleteEntry> titles = titles(
                entry(1, 5081, 5081, "Bakemonogatari", TitleKind.ROMAJI),
                entry(2, 21033, 5081, "Owarimonogatari", TitleKind.ROMAJI),
                entry(3, 21856, 5081, "Owarimonogatari Second Season", TitleKind.ROMAJI));

        assertThat(titles).singleElement()
                .extracting(AutocompleteEntry::label).isEqualTo("Bakemonogatari");
    }

    @Test
    void un_synonyme_ne_sert_jamais_de_libelle() {
        // « SnK » est le plus court de sa serie, et ferait une suggestion illisible.
        List<AutocompleteEntry> titles = titles(
                entry(1, AOT, AOT, "Shingeki no Kyojin", TitleKind.ROMAJI),
                entry(1, AOT, AOT, "SnK", TitleKind.SYNONYM));

        assertThat(titles).singleElement().satisfies(entry -> {
            assertThat(entry.label()).isEqualTo("Shingeki no Kyojin");
            assertThat(entry.aliases()).containsExactly("SnK");
        });
    }

    @Test
    void deux_oeuvres_sans_serie_restent_deux_suggestions() {
        // Catalogue anterieur au regroupement : rien ne doit disparaitre de l'index.
        List<AutocompleteEntry> titles = titles(
                entry(1, AOT, null, "Shingeki no Kyojin", TitleKind.ROMAJI),
                entry(2, 25777, null, "Shingeki no Kyojin Season 2", TitleKind.ROMAJI));

        assertThat(titles).extracting(AutocompleteEntry::label)
                .containsExactly("Shingeki no Kyojin", "Shingeki no Kyojin Season 2");
    }

    @Test
    void les_titres_natifs_restent_ecartes() {
        List<AutocompleteEntry> titles = titles(
                entry(1, AOT, AOT, "Shingeki no Kyojin", TitleKind.ROMAJI),
                entry(1, AOT, AOT, "進撃の巨人", TitleKind.NATIVE));

        assertThat(titles).singleElement().satisfies(entry -> {
            assertThat(entry.label()).isEqualTo("Shingeki no Kyojin");
            assertThat(entry.aliases()).isEmpty();
        });
    }

    @Test
    void le_libelle_prefere_le_titre_anglais_meme_plus_long() {
        // Langue de l'indice « initiales » et de la solution : la suggestion doit lui ressembler.
        List<AutocompleteEntry> result = titles(
                entry(1, 101922, 101922, "Kimetsu no Yaiba", TitleKind.ROMAJI),
                entry(1, 101922, 101922, "Demon Slayer: Kimetsu no Yaiba", TitleKind.ENGLISH));

        assertThat(result).singleElement()
                .satisfies(e -> assertThat(e.label()).isEqualTo("Demon Slayer: Kimetsu no Yaiba"));
    }

    @Test
    void le_titre_generique_anglais_passe_devant_le_romaji() {
        List<AutocompleteEntry> result = titles(
                entry(1, 104578, 104578, "Shingeki no Kyojin 3 Part 2", TitleKind.ROMAJI),
                entry(1, 104578, 104578, "Attack on Titan Season 3 Part 2", TitleKind.ENGLISH),
                entry(2, 16498, 104578, "Shingeki no Kyojin", TitleKind.ROMAJI),
                entry(2, 16498, 104578, "Attack on Titan", TitleKind.ENGLISH));

        assertThat(result).singleElement().satisfies(e -> assertThat(e.label()).isEqualTo("Attack on Titan"));
    }

    @Test
    void sans_titre_anglais_le_romaji_reste_le_libelle() {
        List<AutocompleteEntry> result = titles(entry(1, 5081, 5081, "Bakemonogatari", TitleKind.ROMAJI));

        assertThat(result).singleElement().satisfies(e -> assertThat(e.label()).isEqualTo("Bakemonogatari"));
    }
}
