package com.manguessr.service.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exemples reels tires des synonymes AniList du catalogue.
 *
 * Colonnes : le synonyme, puis les titres de reference de son oeuvre (romaji et anglais),
 * separes par « ; ».
 */
class SuggestibleTitlesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Traductions : aucune n'est sous-chaine ni acronyme du titre de reference.
            "Ataque a los Titanes            | Shingeki no Kyojin;Attack on Titan",
            "Đại Chiến Titan                 | Shingeki no Kyojin;Attack on Titan",
            "進撃の巨人                        | Shingeki no Kyojin;Attack on Titan",
            "Tragones y Mazmorras            | Dungeon Meshi;Delicious in Dungeon",
            "Las chicas de Wilds             | Wild's Girls;Girls of the Wild's",
            "A Menina Que Veio do Outro Lado | Totsukuni no Shoujo;The Girl From the Other Side",
            "Historyjka o pracy zdalnej      | Home Office Romance",
            "Lo Squalificato                 | Ningen Shikkaku;No Longer Human",
            "เพลเยอร์ผู้กลับมาในรอบหมื่นปี            | The Player Who Returned 10000 Years Later",
            "미래일기                          | Mirai Nikki;Future Diary",
            // Titre etranger decore du titre officiel : c'est lui qui prenait une place sur « weak hero ».
            "Weak Hero: Un héroe débil       | Weak Hero",
            "SAKAMOTO DAYS 坂本日常            | SAKAMOTO DAYS",
            "Haikyu!! - Chàng khổng lồ tí hon| Haikyu!!"
    })
    void les_titres_en_langue_etrangere_ne_sont_pas_proposes(String synonym, String references) {
        assertThat(SuggestibleTitles.isVariantOfAny(synonym, List.of(references.split(";"))))
                .as(synonym)
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Abreviations usuelles : acronymes du titre de reference.
            "AoT              | Shingeki no Kyojin;Attack on Titan",
            "SnK              | Shingeki no Kyojin;Attack on Titan",
            // Raccourcis : le synonyme est contenu dans le titre officiel.
            "Demon Slayer     | Kimetsu no Yaiba;Demon Slayer: Kimetsu no Yaiba",
            "Kuroko's Basket  | Kuroko no Basket;Kuroko's Basketball",
            "Elfenlied        | Elfen Lied",
            "Sachiiro no One Room | Sachi-iro no One Room",
            "JoJos Bizarre Adventure | JoJo's Bizarre Adventure"
    })
    void les_abreviations_et_variantes_de_graphie_restent_proposees(String synonym, String references) {
        assertThat(SuggestibleTitles.isVariantOfAny(synonym, List.of(references.split(";"))))
                .as(synonym)
                .isTrue();
    }

    /**
     * Limite assumee : une contraction ne se deduit pas du titre.
     *
     * « FMAB » tient son M d'une majuscule interne (« FullMetal ») absente du titre AniList,
     * dont les initiales donnent « fab » ; « WatashiMote » et « KanoKano » sont des contractions
     * de syllabes. Aucune regle structurelle ne les rattrape, et une liste manuelle serait le
     * retour au cas par cas qu'on vient de supprimer. Elles restent acceptees comme reponse,
     * elles ne sont simplement pas suggerees.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "FMAB         | Fullmetal Alchemist: Brotherhood",
            "WatashiMote  | Watashi ga Motenai no wa Dou Kangaetemo Omaera ga Warui!;Kiss Him, Not Me",
            "KanoKano     | Kanojo mo Kanojo;Girlfriend, Girlfriend"
    })
    void les_contractions_de_syllabes_ne_sont_pas_rattrapees(String synonym, String references) {
        assertThat(SuggestibleTitles.isVariantOfAny(synonym, List.of(references.split(";"))))
                .as(synonym)
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "go   | Dragon Ball;Bungo Stray Dogs",
            "     | Attack on Titan",
            "a    | Attack on Titan"
    })
    void une_chaine_trop_courte_ne_passe_pas_par_hasard(String synonym, String references) {
        assertThat(SuggestibleTitles.isVariantOfAny(synonym, List.of(references.split(";"))))
                .as("[%s]", synonym)
                .isFalse();
    }
}
