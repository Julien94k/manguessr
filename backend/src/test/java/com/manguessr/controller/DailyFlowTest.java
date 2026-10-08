package com.manguessr.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manguessr.model.entity.DailyPuzzle;
import com.manguessr.model.entity.PuzzleRound;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.DailyPuzzleRepository;
import com.manguessr.repository.GameSessionRepository;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.DailyPuzzleGenerator;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Puzzle quotidien fige, classement, serie et statistiques, de bout en bout.
 *
 * Les dates explicites sont placees loin dans le futur : la base H2 est partagee par toutes
 * les classes de test, et un puzzle genere ici ne doit pas se retrouver dans leur fenetre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaWorkRepository workRepository;

    @Autowired
    private GameSessionRepository sessionRepository;

    @Autowired
    private DailyPuzzleRepository puzzleRepository;

    @Autowired
    private DailyPuzzleGenerator generator;

    @BeforeAll
    @Transactional
    void seedCatalog() {
        TestCatalog.seedAnimeIfEmpty(workRepository);
    }

    // --- Helpers ---------------------------------------------------------------------------

    private JsonNode perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String register(String username) throws Exception {
        return perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", username + "@daily.test",
                                "username", username,
                                "password", "motdepasse1"))),
                null).get("token").asText();
    }

    private long start(String token, GameMode mode, boolean unlimited) throws Exception {
        return perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("mode", mode.name(), "theme", "ANIME", "unlimited", unlimited))),
                token).get("id").asLong();
    }

    private List<SessionRound> roundsOf(long sessionId) {
        return sessionRepository.findByIdWithRounds(sessionId).orElseThrow().getRounds().stream()
                .sorted(Comparator.comparingInt(SessionRound::getOrdinal))
                .toList();
    }

    private List<Long> workIdsOf(long sessionId) {
        return roundsOf(sessionId).stream().map(round -> round.getWork().getId()).toList();
    }

    /** Gagne la manche unique d'une partie Wordle au premier essai : 10 000 points. */
    private void winWordle(String token, long sessionId) throws Exception {
        String answer = roundsOf(sessionId).get(0).getWork().displayTitle();
        JsonNode response = perform(post("/api/sessions/" + sessionId + "/guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roundOrdinal", 0, "answer", answer))),
                token);
        assertThat(response.get("correct").asBoolean()).isTrue();
        assertThat(response.get("session").get("status").asText()).isEqualTo("FINISHED");
    }

    private Optional<JsonNode> entryFor(JsonNode leaderboard, String username) {
        for (JsonNode entry : leaderboard.get("entries")) {
            if (entry.get("username").asText().equals(username)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    // --- Puzzle fige -----------------------------------------------------------------------

    @Test
    void tous_les_joueurs_recoivent_le_meme_puzzle_du_jour() throws Exception {
        long first = start(register("meme-puzzle-a"), GameMode.IMAGES, false);
        long second = start(register("meme-puzzle-b"), GameMode.IMAGES, false);
        long anonymous = start(null, GameMode.IMAGES, false);

        assertThat(workIdsOf(first)).hasSize(3).doesNotHaveDuplicates();
        assertThat(workIdsOf(second)).isEqualTo(workIdsOf(first));
        assertThat(workIdsOf(anonymous)).isEqualTo(workIdsOf(first));
        // Le contenu tire (images) est lui aussi identique, pas seulement l'oeuvre.
        assertThat(roundsOf(second).get(0).getPayload()).isEqualTo(roundsOf(first).get(0).getPayload());
    }

    @Test
    void la_generation_est_idempotente() {
        LocalDate date = LocalDate.of(2031, 6, 1);
        generator.ensure(date, GameMode.WORDLE, WorkType.ANIME);
        Long firstId = puzzleRepository.findWithRounds(date, GameMode.WORDLE, WorkType.ANIME).orElseThrow().getId();

        generator.ensure(date, GameMode.WORDLE, WorkType.ANIME);

        assertThat(puzzleRepository.findWithRounds(date, GameMode.WORDLE, WorkType.ANIME).orElseThrow().getId())
                .isEqualTo(firstId);
        assertThat(puzzleRepository.findAll().stream()
                .filter(puzzle -> puzzle.getPuzzleDate().equals(date) && puzzle.getMode() == GameMode.WORDLE)
                .count()).isEqualTo(1);
    }

    @Test
    void une_oeuvre_ne_revient_pas_tant_que_le_catalogue_le_permet() {
        // 9 oeuvres jouables en mode Images, 3 par jour : trois jours sans aucune repetition.
        LocalDate start = LocalDate.of(2032, 1, 1);
        Set<Long> seen = new HashSet<>();
        int drawn = 0;

        for (int day = 0; day < 3; day++) {
            LocalDate date = start.plusDays(day);
            generator.ensure(date, GameMode.IMAGES, WorkType.ANIME);
            DailyPuzzle puzzle = puzzleRepository.findWithRounds(date, GameMode.IMAGES, WorkType.ANIME).orElseThrow();
            for (PuzzleRound round : puzzle.getRounds()) {
                seen.add(round.getWork().getId());
                drawn++;
            }
        }

        assertThat(drawn).isEqualTo(9);
        assertThat(seen).hasSize(9);

        // Historique epuise : le quatrieme jour reutilise une oeuvre plutot que de ne rien proposer.
        assertThatCode(() -> generator.ensure(start.plusDays(3), GameMode.IMAGES, WorkType.ANIME))
                .doesNotThrowAnyException();
    }

    // --- Classement, serie, profil ---------------------------------------------------------

    @Test
    void une_partie_quotidienne_terminee_alimente_classement_serie_et_profil() throws Exception {
        String token = register("vainqueur");
        long sessionId = start(token, GameMode.WORDLE, false);
        winWordle(token, sessionId);

        JsonNode leaderboard = perform(get("/api/leaderboard").param("period", "daily"), token);
        assertThat(entryFor(leaderboard, "vainqueur")).get()
                .satisfies(entry -> assertThat(entry.get("score").asLong()).isEqualTo(10_000));
        assertThat(leaderboard.get("me").get("username").asText()).isEqualTo("vainqueur");

        JsonNode stats = perform(get("/api/profile/stats"), token);
        assertThat(stats.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(stats.get("bestStreak").asInt()).isEqualTo(1);
        assertThat(stats.get("gamesPlayed").asLong()).isEqualTo(1);
        assertThat(stats.get("totalScore").asLong()).isEqualTo(10_000);
        assertThat(stats.get("history").get(0).get("mode").asText()).isEqualTo("WORDLE");

        JsonNode daily = perform(get("/api/daily").param("theme", "anime"), token);
        assertThat(daily.get("currentStreak").asInt()).isEqualTo(1);
        JsonNode wordle = null;
        for (JsonNode mode : daily.get("modes")) {
            if (mode.get("mode").asText().equals("WORDLE")) {
                wordle = mode;
            }
        }
        assertThat(wordle).isNotNull();
        assertThat(wordle.get("status").asText()).isEqualTo("FINISHED");
        assertThat(wordle.get("score").asInt()).isEqualTo(10_000);
    }

    @Test
    void les_parties_libres_ne_comptent_ni_au_classement_ni_dans_la_serie() throws Exception {
        String token = register("joueur-libre");
        long sessionId = start(token, GameMode.WORDLE, true);
        winWordle(token, sessionId);

        JsonNode leaderboard = perform(get("/api/leaderboard").param("period", "alltime"), token);
        assertThat(entryFor(leaderboard, "joueur-libre")).isEmpty();
        assertThat(leaderboard.get("me").isNull()).isTrue();

        JsonNode stats = perform(get("/api/profile/stats"), token);
        assertThat(stats.get("currentStreak").asInt()).isZero();
        assertThat(stats.get("gamesPlayed").asLong()).isZero();
    }

    @Test
    void le_defi_du_jour_est_consultable_sans_compte() throws Exception {
        JsonNode daily = perform(get("/api/daily").param("theme", "anime"), null);

        assertThat(daily.get("currentStreak").isNull()).isTrue();
        assertThat(daily.get("secondsUntilNext").asLong()).isBetween(0L, 86_400L);
        assertThat(daily.get("modes")).hasSize(GameMode.forType(WorkType.ANIME).size());
    }

    @Test
    void les_statistiques_exigent_un_compte_et_les_parametres_inconnus_sont_refuses() throws Exception {
        mockMvc.perform(get("/api/profile/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaderboard").param("period", "yearly")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/daily").param("theme", "cinema")).andExpect(status().isBadRequest());
    }
}
