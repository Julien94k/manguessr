package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tirage des quatre portraits d'une manche, sur un catalogue en memoire calque sur le reel.
 *
 * Les trois defauts reproduits ici ont ete mesures sur la base de production le 16/09/2026 :
 * la fiche « Narrator » sortait dans une manche anime sur cinq, une manche sur dix montrait
 * deux fois la meme personne, et le tirage limite aux deux premiers personnages ne laissait
 * que 1261 visages distincts pour 1000 animes.
 */
class CharactersModeHandlerTest {

    /** Fiche AniList rattachee a 146 animes du catalogue : un artefact, pas un personnage. */
    private static final int NARRATOR = 36309;

    /** Personnage reel partage par plusieurs oeuvres : Levi appartient a douze animes. */
    private static final int SHARED_PERSON = 45627;

    private static final int WORKS = 6;
    private static final int UNIQUE_CHARACTERS_PER_WORK = 5;

    private CharactersModeHandler handler;
    private final Map<Long, MediaCharacter> charactersById = new HashMap<>();

    @BeforeEach
    void setUp() {
        charactersById.clear();

        MediaWorkRepository workRepository = mock(MediaWorkRepository.class);
        MediaCharacterRepository characterRepository = mock(MediaCharacterRepository.class);

        List<Long> pool = new ArrayList<>();
        for (long workId = 1; workId <= WORKS; workId++) {
            MediaWork work = buildWork(workId);
            pool.add(workId);
            when(workRepository.findById(workId)).thenReturn(Optional.of(work));
        }

        when(workRepository.findCharacterModePool(any(), any(), anyLong(), anyList())).thenReturn(pool);
        when(characterRepository.findAnilistIdsSharedByManyWorks(anyLong())).thenReturn(List.of(NARRATOR));

        handler = new CharactersModeHandler(workRepository, characterRepository,
                mock(AnswerMatcher.class), mock(ScoreCalculator.class),
                new GameProperties(), mock(RoundSupport.class));
    }

    /**
     * Une oeuvre du catalogue de test : la fiche generique y est la plus populaire, comme
     * dans la vraie base, donc l'ancien tirage « parmi les deux premiers » la servait presque
     * toujours.
     */
    private MediaWork buildWork(long workId) {
        MediaWork work = new MediaWork();
        work.setId(workId);
        work.setAnilistId((int) workId);
        work.setType(WorkType.ANIME);
        work.setDifficultyTier(Difficulty.EASY);
        work.setTitleRomaji("Oeuvre " + workId);

        addCharacter(work, workId * 100, "Narrator", NARRATOR, 10_000);
        // Les quatre premieres oeuvres partagent une meme personne, comme les saisons d'une serie.
        if (workId <= 4) {
            addCharacter(work, workId * 100 + 1, "Levi", SHARED_PERSON, 9_000);
        }
        for (int i = 0; i < UNIQUE_CHARACTERS_PER_WORK; i++) {
            addCharacter(work, workId * 100 + 10 + i, "Perso " + workId + "-" + i,
                    (int) (1_000 + workId * 10 + i), 900 - i * 100);
        }
        return work;
    }

    private void addCharacter(MediaWork work, long id, String name, int anilistId, int favourites) {
        MediaCharacter character = new MediaCharacter();
        character.setId(id);
        character.setName(name);
        character.setNormalizedName(name.toLowerCase());
        character.setAnilistId(anilistId);
        character.setImageUrl("https://cdn.example/perso-" + id + ".png");
        character.setFavourites(favourites);
        work.addCharacter(character);
        charactersById.put(id, character);
    }

    private List<MediaCharacter> draw(long seed) {
        return handler.prepare(charactersById.get(100L).getWork(), seed)
                .characterIds().stream().map(charactersById::get).toList();
    }

    @Test
    void une_fiche_generique_n_est_jamais_tiree() {
        for (long seed = 0; seed < 200; seed++) {
            assertThat(draw(seed))
                    .as("graine %d", seed)
                    .noneMatch(character -> character.getAnilistId() == NARRATOR);
        }
    }

    @Test
    void une_apparition_hors_de_l_oeuvre_d_origine_n_est_jamais_tiree() {
        // Denji figure dans « Dandadan » avec plus de favoris que tout le casting : tire depuis
        // cette fiche, le portrait aurait eu « Dandadan » pour solution.
        for (long workId = 1; workId <= WORKS; workId++) {
            MediaWork work = charactersById.get(workId * 100).getWork();
            addCharacter(work, workId * 100 + 99, "Denji", 130102, 50_000);
            charactersById.get(workId * 100 + 99).setGuest(true);
        }

        for (long seed = 0; seed < 200; seed++) {
            assertThat(draw(seed))
                    .as("graine %d", seed)
                    .noneMatch(MediaCharacter::isGuest);
        }
    }

    @Test
    void deux_oeuvres_de_la_meme_serie_ne_fournissent_pas_deux_portraits() {
        // Sinon le meme titre repondrait a deux portraits : 500 points offerts, depuis qu'une
        // serie entiere vaut une reponse.
        MediaWork anchor = charactersById.get(100L).getWork();
        MediaWork sameSeries = charactersById.get(200L).getWork();
        anchor.setSeriesId(anchor.getAnilistId());
        sameSeries.setSeriesId(anchor.getAnilistId());

        for (long seed = 0; seed < 200; seed++) {
            assertThat(draw(seed))
                    .as("graine %d", seed)
                    .extracting(character -> character.getWork().getId())
                    .doesNotContain(sameSeries.getId());
        }
    }

    @Test
    void une_manche_ne_montre_jamais_deux_fois_la_meme_personne() {
        for (long seed = 0; seed < 200; seed++) {
            List<MediaCharacter> drawn = draw(seed);

            assertThat(drawn).as("graine %d", seed).hasSize(4);
            assertThat(drawn.stream().map(MediaCharacter::getAnilistId).collect(java.util.stream.Collectors.toSet()))
                    .as("graine %d : quatre personnes distinctes, pas seulement quatre oeuvres", seed)
                    .hasSize(4);
        }
    }

    @Test
    void le_tirage_ne_se_limite_plus_aux_deux_personnages_les_plus_populaires() {
        Set<String> seenInAnchorWork = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            for (MediaCharacter character : draw(seed)) {
                if (character.getWork().getId() == 1L) {
                    seenInAnchorWork.add(character.getName());
                }
            }
        }

        // L'oeuvre ancre compte six portraits exploitables (« Narrator » exclu). L'ancien
        // tirage n'en servait que deux ; on en attend nettement plus.
        assertThat(seenInAnchorWork).hasSizeGreaterThan(3);
    }

    private MediaWork work(long id) {
        return charactersById.get(id * 100).getWork();
    }

    private List<MediaCharacter> portraits(RoundPayload payload) {
        return payload.characterIds().stream().map(charactersById::get).toList();
    }

    private static String person(MediaCharacter character) {
        return CharactersModeHandler.personKey(character);
    }

    @Test
    void une_personne_ne_revient_pas_d_une_manche_a_l_autre_de_la_meme_partie() {
        for (long seed = 0; seed < 200; seed++) {
            DraftMemory memory = DraftMemory.empty();
            List<MediaCharacter> game = new ArrayList<>();
            // Trois manches, trois ancres differentes, une seule memoire : comme RoundDrafter.
            for (long anchor = 1; anchor <= 3; anchor++) {
                game.addAll(portraits(handler.prepare(work(anchor), seed + anchor * 7919, memory)));
            }

            assertThat(game).as("graine %d", seed).hasSize(12);
            assertThat(game.stream().map(CharactersModeHandlerTest::person).distinct().count())
                    .as("graine %d : Levi (quatre oeuvres) ne doit sortir qu'une fois par partie", seed)
                    .isEqualTo(12);
        }
    }

    @Test
    void les_personnes_vues_recemment_ne_sont_retenues_qu_a_defaut_d_autres() {
        MediaWork anchor = work(1);
        Set<String> seen = new HashSet<>();
        for (MediaCharacter character : anchor.getCharacters()) {
            if (!character.getName().equals("Perso 1-4")) {
                seen.add(person(character));
            }
        }

        for (long seed = 0; seed < 100; seed++) {
            List<MediaCharacter> drawn = portraits(
                    handler.prepare(anchor, seed, DraftMemory.recent(Set.of(), seen)));
            assertThat(drawn)
                    .as("graine %d : seul personnage de l'ancre encore jamais vu", seed)
                    .filteredOn(character -> character.getWork().getId() == 1L)
                    .extracting(MediaCharacter::getName)
                    .containsExactly("Perso 1-4");
        }
    }

    @Test
    void les_oeuvres_vues_recemment_ne_completent_la_manche_qu_en_dernier() {
        for (long seed = 0; seed < 100; seed++) {
            // Une memoire par partie : elle accumule les personnes montrees.
            DraftMemory recent = DraftMemory.recent(Set.of(2L, 3L, 4L), Set.of());
            Set<Long> works = new HashSet<>();
            for (MediaCharacter character : portraits(handler.prepare(work(1), seed, recent))) {
                works.add(character.getWork().getId());
            }
            // Ancre 1, puis les deux seules oeuvres fraiches, puis une recente faute de mieux.
            assertThat(works).as("graine %d", seed).hasSize(4).contains(1L, 5L, 6L);
        }
    }

    @Test
    void une_oeuvre_difficile_ne_tire_que_parmi_ses_personnages_les_plus_connus() {
        MediaWork hard = work(1);
        hard.setDifficultyTier(Difficulty.HARD);

        Set<String> names = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            portraits(handler.prepare(hard, seed)).stream()
                    .filter(character -> character.getWork().getId() == 1L)
                    .forEach(character -> names.add(character.getName()));
        }

        // Six personnages exploitables (Narrator exclu) ; en difficile, les cinq premiers seulement.
        assertThat(names).hasSize(5).doesNotContain("Perso 1-4");
    }
}
