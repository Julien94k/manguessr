package com.manguessr.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manguessr.model.entity.*;
import com.manguessr.model.enums.*;
import com.manguessr.repository.GameSessionRepository;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.support.TestCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet d'une partie, sur un catalogue minimal monte en memoire.
 *
 * Le test qui compte vraiment ici est {@code la_reponse_ne_fuite_jamais_avant_la_fin} :
 * tout le reste du jeu n'a d'interet que si la reponse reste cote serveur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaWorkRepository workRepository;

    @Autowired
    private GameSessionRepository sessionRepository;

    @Autowired
    private com.manguessr.repository.MediaCharacterRepository characterRepository;

    private static final String SECRET_TITLE = TestCatalog.SECRET_TITLE;

    @BeforeAll
    @Transactional
    void seedCatalog() {
        TestCatalog.seedAnimeIfEmpty(workRepository);
    }

    private JsonNode startSession(String mode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mode", mode, "theme", "ANIME", "unlimited", true))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Variante authentifiee : seule une partie rattachee a un joueur peut avoir une memoire. */
    private JsonNode startSession(String mode, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mode", mode, "theme", "ANIME", "unlimited", true))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", username + "@game.test",
                                "username", username,
                                "password", "motdepasse1"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("token").asText();
    }

    private Set<Long> workIdsOf(long sessionId) throws Exception {
        return sessionRepository.findByIdWithRounds(sessionId).orElseThrow().getRounds().stream()
                .map(round -> round.getWork().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private JsonNode guess(long sessionId, int ordinal, String answer) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions/" + sessionId + "/guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("roundOrdinal", ordinal, "answer", answer))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void la_reponse_ne_fuite_jamais_avant_la_fin_de_la_manche() throws Exception {
        JsonNode session = startSession("IMAGES");
        String payload = session.toString();

        // Le titre a deviner ne doit apparaitre nulle part dans la reponse HTTP.
        assertThat(payload).doesNotContain(SECRET_TITLE);
        assertThat(payload).doesNotContain("Oeuvre EASY", "Oeuvre MEDIUM", "Oeuvre HARD");

        // Ni les URLs d'origine, dont les chemins trahissent l'oeuvre.
        assertThat(payload).doesNotContain("cdn.example");

        for (JsonNode round : session.get("rounds")) {
            assertThat(round.get("solution").isNull())
                    .as("manche %s : la solution doit rester nulle", round.get("ordinal"))
                    .isTrue();
            assertThat(round.get("titleHint").isNull()).isTrue();
        }
    }

    @Test
    void les_generiques_passent_par_le_proxy_signe() throws Exception {
        JsonNode session = startSession("OPENING");
        String payload = session.toString();

        // Les fichiers AnimeThemes s'appellent « DarlingInTheFranXX-OP1.ogg » : l'URL
        // d'origine donnerait la reponse des la premiere seconde.
        assertThat(payload).doesNotContain("cdn.example", "Oeuvre_");

        for (JsonNode round : session.get("rounds")) {
            JsonNode media = round.get("media");
            assertThat(media).hasSize(1);
            assertThat(media.get(0).get("kind").asText()).isEqualTo("AUDIO");
            assertThat(media.get(0).get("url").asText()).startsWith("/api/media/stream/");
        }
    }

    @Test
    void un_jeton_ne_sert_que_son_type_de_media() throws Exception {
        String audioUrl = startSession("OPENING").get("rounds").get(0).get("media").get(0).get("url").asText();
        String imageUrl = startSession("IMAGES").get("rounds").get(0).get("media").get(0).get("url").asText();

        // Un jeton de piste audio ne doit pas etre rendu comme image, et reciproquement :
        // le refus intervient avant tout appel a la source.
        mockMvc.perform(get(audioUrl.replace("/stream/", "/img/")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(imageUrl.replace("/img/", "/stream/")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void le_score_suit_le_bareme_des_indices() throws Exception {
        JsonNode session = startSession("IMAGES");
        long id = session.get("id").asLong();

        String title = resolveAnswer(id, 0);

        // Deux indices debloques : 10 000 - 2 x 2500 = 5000.
        mockMvc.perform(post("/api/sessions/" + id + "/clue").param("round", "0"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/" + id + "/clue").param("round", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(5000));

        JsonNode response = guess(id, 0, title);

        assertThat(response.get("correct").asBoolean()).isTrue();
        assertThat(response.get("round").get("score").asInt()).isEqualTo(5000);
        // La solution n'est revelee qu'une fois la manche fermee.
        assertThat(response.get("round").get("solution").isNull()).isFalse();
    }

    @Test
    void une_manche_fermee_refuse_toute_nouvelle_tentative() throws Exception {
        JsonNode session = startSession("IMAGES");
        long id = session.get("id").asLong();

        guess(id, 0, "une reponse fausse");

        mockMvc.perform(post("/api/sessions/" + id + "/guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("roundOrdinal", 0, "answer", "encore une"))))
                .andExpect(status().isConflict());
    }

    @Test
    void le_mode_wordle_refuse_un_doublon_sans_consommer_d_essai() throws Exception {
        JsonNode session = startSession("WORDLE");
        long id = session.get("id").asLong();

        // L'oeuvre est tiree au hasard : proposer un titre fixe tombait parfois juste, la
        // manche se fermait et le doublon recevait un 409. On propose donc une autre oeuvre.
        String answer = resolveAnswer(id, 0);
        String firstGuess = "Oeuvre HARD 1".equals(answer) ? "Oeuvre HARD 2" : "Oeuvre HARD 1";
        JsonNode first = guess(id, 0, firstGuess);
        int attemptsAfterFirst = first.get("round").get("attemptsUsed").asInt();

        JsonNode duplicate = guess(id, 0, firstGuess);

        assertThat(duplicate.get("message").asText()).contains("déjà proposé");
        assertThat(duplicate.get("round").get("attemptsUsed").asInt()).isEqualTo(attemptsAfterFirst);
    }

    @Test
    void le_mode_wordle_rejette_un_titre_hors_catalogue() throws Exception {
        JsonNode session = startSession("WORDLE");
        long id = session.get("id").asLong();

        JsonNode response = guess(id, 0, "Un titre totalement invente");

        assertThat(response.get("message").asText()).contains("inconnu");
        assertThat(response.get("round").get("attemptsUsed").asInt()).isZero();
    }

    @Test
    void un_mode_indisponible_dans_l_univers_est_refuse() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mode", "COVER_REVEAL", "theme", "ANIME", "unlimited", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("n'existe pas")));
    }

    @Test
    void la_liste_des_modes_reflete_l_univers_demande() throws Exception {
        mockMvc.perform(get("/api/games").param("theme", "anime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'OPENING')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'COVER_REVEAL')]").doesNotExist());

        mockMvc.perform(get("/api/games").param("theme", "manga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'COVER_REVEAL')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'OPENING')]").doesNotExist());
    }

    /**
     * Regle reprise d'AniGuessr : les quatre portraits viennent d'oeuvres differentes.
     *
     * Sinon reconnaitre un seul personnage donnerait les quatre titres d'un coup,
     * soit 2000 points offerts sur les 10 000 de la manche.
     */
    @Test
    void le_mode_personnages_tire_quatre_oeuvres_differentes() throws Exception {
        JsonNode session = startSession("CHARACTERS");
        long id = session.get("id").asLong();

        for (JsonNode round : session.get("rounds")) {
            assertThat(round.get("characters")).hasSize(4);
        }

        // On repond n'importe quoi pour fermer la manche et obtenir les titres reveles.
        mockMvc.perform(post("/api/sessions/" + id + "/guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roundOrdinal", 0,
                                "entries", List.of(
                                        Map.of("character", "", "title", ""),
                                        Map.of("character", "", "title", ""),
                                        Map.of("character", "", "title", ""),
                                        Map.of("character", "", "title", ""))))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/sessions/" + id)).andReturn();
        JsonNode round = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("rounds").get(0);

        List<String> titles = new java.util.ArrayList<>();
        for (JsonNode slot : round.get("characters")) {
            titles.add(slot.get("workTitle").asText());
        }

        assertThat(titles).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    void le_mode_personnages_note_chaque_titre_contre_l_oeuvre_de_son_portrait() throws Exception {
        JsonNode session = startSession("CHARACTERS");
        long id = session.get("id").asLong();

        List<String> expectedTitles = characterWorkTitles(id, 0);

        // On ne remplit que le titre du premier portrait : il ne doit valider que celui-la.
        List<Map<String, String>> entries = new java.util.ArrayList<>();
        entries.add(Map.of("character", "", "title", expectedTitles.get(0)));
        for (int slot = 1; slot < expectedTitles.size(); slot++) {
            entries.add(Map.of("character", "", "title", ""));
        }

        MvcResult result = mockMvc.perform(post("/api/sessions/" + id + "/guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("roundOrdinal", 0, "entries", entries))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        // Un seul titre trouve : 500 points, et non 4 x 500.
        assertThat(response.get("round").get("score").asInt()).isEqualTo(500);

        JsonNode slots = response.get("round").get("characters");
        assertThat(slots.get(0).get("titleFound").asBoolean()).isTrue();
        for (int slot = 1; slot < slots.size(); slot++) {
            assertThat(slots.get(slot).get("titleFound").asBoolean())
                    .as("portrait %d", slot)
                    .isFalse();
        }
    }

    /**
     * Une partie libre ne repropose pas les oeuvres des dernieres manches du joueur.
     *
     * Sans cette memoire, chaque partie retire a neuf dans tout le vivier : cote manga, les
     * 126 oeuvres jouables en mode Images ramenaient les memes series au bout de quelques
     * parties. Un joueur anonyme reste sans memoire, faute d'identite cote serveur.
     */
    @Test
    void une_partie_libre_evite_les_oeuvres_deja_vues_par_le_joueur() throws Exception {
        String token = register("joueurmemoire");

        Set<Long> first = workIdsOf(startSession("IMAGES", token).get("id").asLong());
        Set<Long> second = workIdsOf(startSession("IMAGES", token).get("id").asLong());

        assertThat(first).hasSize(3);
        assertThat(second).doesNotContainAnyElementsOf(first);
    }

    @Test
    void passer_une_manche_la_ferme_a_zero_point() throws Exception {
        JsonNode session = startSession("IMAGES");
        long id = session.get("id").asLong();

        MvcResult result = mockMvc.perform(post("/api/sessions/" + id + "/skip").param("round", "0"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode updated = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode round = updated.get("rounds").get(0);

        assertThat(round.get("status").asText()).isEqualTo("SKIPPED");
        assertThat(round.get("score").asInt()).isZero();
    }

    /**
     * Lit la bonne reponse directement en base.
     *
     * C'est precisement ce que le client ne peut pas faire : l'API ne la revele qu'une fois
     * la manche fermee. Le test doit donc passer par le depot pour pouvoir jouer juste.
     */
    /** Titres des oeuvres des quatre portraits, lus en base. */
    List<String> characterWorkTitles(long sessionId, int ordinal) {
        com.manguessr.model.entity.SessionRound round =
                sessionRepository.findByIdWithRounds(sessionId).orElseThrow()
                        .getRounds().stream()
                        .filter(candidate -> candidate.getOrdinal() == ordinal)
                        .findFirst().orElseThrow();

        return characterIdsOf(round).stream()
                .map(characterId -> characterRepository.findWithWork(characterId).orElseThrow())
                .map(character -> character.getWork().displayTitle())
                .toList();
    }

    private List<Long> characterIdsOf(com.manguessr.model.entity.SessionRound round) {
        try {
            JsonNode payload = objectMapper.readTree(round.getPayload());
            List<Long> ids = new java.util.ArrayList<>();
            payload.get("characterIds").forEach(node -> ids.add(node.asLong()));
            return ids;
        } catch (Exception e) {
            throw new IllegalStateException("Contenu de manche illisible", e);
        }
    }

    String resolveAnswer(long sessionId, int ordinal) {
        return sessionRepository.findByIdWithRounds(sessionId)
                .orElseThrow()
                .getRounds().stream()
                .filter(round -> round.getOrdinal() == ordinal)
                .findFirst()
                .orElseThrow()
                .getWork()
                .displayTitle();
    }
}
