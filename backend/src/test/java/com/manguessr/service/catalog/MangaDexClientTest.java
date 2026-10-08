package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Selection des pages de chapitre du mode Images manga. Une mauvaise selection ne leve rien :
 * elle servirait une page de titre (qui donne la reponse) ou une page de credits (indevinable).
 */
class MangaDexClientTest {

    private static MangaDexClient.ChapterRef chapter(String number, int pages) {
        return new MangaDexClient.ChapterRef("id-" + number + "-" + pages, number, pages);
    }

    @Test
    void la_repartition_espace_regulierement_les_elements() {
        List<Integer> thirty = IntStream.range(0, 30).boxed().toList();

        assertThat(MangaDexClient.spread(thirty, 3)).containsExactly(5, 15, 25);
        assertThat(MangaDexClient.spread(List.of(1, 2), 5)).containsExactly(1, 2);
        assertThat(MangaDexClient.spread(List.of(), 3)).isEmpty();
    }

    @Test
    void les_pages_de_garde_ne_sont_jamais_retenues() {
        List<String> files = IntStream.range(0, 20).mapToObj(i -> "p" + i + ".jpg").toList();

        List<String> interior = MangaDexClient.interiorPages(files, 20);

        assertThat(interior).doesNotContain("p0.jpg", "p1.jpg", "p18.jpg", "p19.jpg");
        assertThat(MangaDexClient.interiorPages(files, 2)).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void un_chapitre_trop_court_ne_fournit_aucune_page() {
        List<String> files = IntStream.range(0, MangaDexClient.MIN_CHAPTER_PAGES - 1)
                .mapToObj(i -> "p" + i + ".jpg").toList();

        assertThat(MangaDexClient.interiorPages(files, 2)).isEmpty();
    }

    @Test
    void une_seule_traduction_par_chapitre_et_pas_de_chapitre_court_ou_sans_numero() {
        List<MangaDexClient.ChapterRef> feed = List.of(
                chapter("1", 40), chapter("1", 38),   // meme chapitre, deux traductions
                chapter("2", 3),                      // annonce trop courte
                chapter(null, 30),                    // one-shot sans numero
                chapter("3", 25));

        List<MangaDexClient.ChapterRef> selected = MangaDexClient.selectChapters(feed, 5);

        assertThat(selected).extracting(MangaDexClient.ChapterRef::chapter).containsExactly("1", "3");
        assertThat(selected.get(0).pages()).isEqualTo(40);
    }

    @Test
    void seuls_les_premiers_chapitres_sont_candidats_pour_limiter_les_spoilers() {
        List<MangaDexClient.ChapterRef> feed = new ArrayList<>();
        for (int number = 1; number <= 200; number++) {
            feed.add(chapter(String.valueOf(number), 20));
        }

        List<MangaDexClient.ChapterRef> selected = MangaDexClient.selectChapters(feed, 3);

        assertThat(selected).hasSize(3);
        assertThat(selected).allSatisfy(ref ->
                assertThat(Integer.parseInt(ref.chapter())).isLessThanOrEqualTo(MangaDexClient.EARLY_CHAPTERS));
    }

    @Test
    void la_recherche_par_titre_ne_retient_que_l_oeuvre_au_meme_identifiant_anilist() throws Exception {
        // Recherche « Sakamoto » : l'homonyme arrive en tete, le bon manga ensuite.
        JsonNode results = new ObjectMapper().readTree("""
                [
                  {"id": "homonyme", "attributes": {"links": {"al": "999"}}},
                  {"id": "sans-lien", "attributes": {"links": null}},
                  {"id": "lien-invalide", "attributes": {"links": {"al": "abc"}}},
                  {"id": "le-bon", "attributes": {"links": {"al": " 86082 "}}}
                ]
                """);

        assertThat(MangaDexClient.matchAniListId(results, 86082)).contains("le-bon");
        assertThat(MangaDexClient.matchAniListId(results, 12345)).isEmpty();
    }
}
