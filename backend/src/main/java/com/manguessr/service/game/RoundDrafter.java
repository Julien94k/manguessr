package com.manguessr.service.game;

import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.mode.DraftMemory;
import com.manguessr.service.game.mode.GameModeHandler;
import com.manguessr.service.game.mode.RoundSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tire les manches d'un puzzle : une oeuvre par palier de difficulte, et son contenu fige.
 *
 * Partage par les parties libres (graine aleatoire) et par le puzzle quotidien (graine
 * stable, avec un historique d'oeuvres a eviter).
 */
@Component
public class RoundDrafter {

    private static final Logger log = LoggerFactory.getLogger(RoundDrafter.class);

    /** Decalage de graine entre manches : un nombre premier evite des tirages correles. */
    private static final long ROUND_SEED_STEP = 7919L;

    private final Map<GameMode, GameModeHandler> handlers = new EnumMap<>(GameMode.class);
    private final MediaWorkRepository workRepository;
    private final RoundSupport support;

    public RoundDrafter(List<GameModeHandler> modeHandlers,
                        MediaWorkRepository workRepository,
                        RoundSupport support) {
        for (GameModeHandler handler : modeHandlers) {
            handlers.put(handler.mode(), handler);
        }
        this.workRepository = workRepository;
        this.support = support;
    }

    /**
     * Une manche tiree, prete a etre copiee dans une partie ou un puzzle.
     *
     * @param payload contenu serialise (images, personnages, generique)
     */
    public record Draft(int ordinal, Difficulty difficulty, MediaWork work, String payload) {}

    /**
     * Tire les manches d'un mode.
     *
     * @param avoid oeuvres a eviter (historique du puzzle quotidien). Si le catalogue ne permet
     *              pas de les eviter, elles redeviennent tirables : une oeuvre vue il y a
     *              quelques mois vaut mieux qu'un puzzle absent.
     * @throws IllegalStateException si le catalogue est trop mince pour ce mode
     */
    public List<Draft> draft(GameMode mode, WorkType theme, long baseSeed, Set<Long> avoid) {
        return draft(mode, theme, baseSeed, avoid, DraftMemory.empty());
    }

    /**
     * Tire les manches d'une partie libre, en tenant compte de ce que le joueur a vu recemment.
     * La meme memoire traverse les trois manches : une personne montree en manche 1 ne revient
     * pas en manche 3.
     */
    public List<Draft> draft(GameMode mode, WorkType theme, long baseSeed, DraftMemory memory) {
        return draft(mode, theme, baseSeed, memory.recentWorks(), memory);
    }

    private List<Draft> draft(GameMode mode, WorkType theme, long baseSeed, Set<Long> avoidWorks, DraftMemory memory) {
        GameModeHandler handler = handlers.get(mode);
        if (handler == null) {
            throw new IllegalArgumentException("Mode non implemente : " + mode);
        }

        Set<Long> avoid = expandToSeries(avoidWorks);

        List<Difficulty> tiers = mode.getRoundsPerPuzzle() == 1
                ? List.of(Difficulty.MEDIUM)
                : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);

        Set<Long> used = new HashSet<>();
        List<Draft> drafts = new ArrayList<>();

        for (int ordinal = 0; ordinal < tiers.size(); ordinal++) {
            Difficulty tier = tiers.get(ordinal);
            long seed = baseSeed + ordinal * ROUND_SEED_STEP;

            Long workId = pickWork(handler, theme, tier, seed, used, avoid);
            if (workId == null && !avoid.isEmpty()) {
                log.info("Historique epuise pour {} {} : une oeuvre deja tiree redevient eligible", mode, theme);
                workId = pickWork(handler, theme, tier, seed, used, Set.of());
            }
            if (workId == null) {
                throw new IllegalStateException(
                        "Catalogue insuffisant pour le mode " + mode + " en "
                                + theme.name().toLowerCase(Locale.ROOT)
                                + ". Lancez une ingestion (POST /api/catalog/ingestion).");
            }
            MediaWork work = workRepository.findById(workId).orElseThrow();
            // Toute la serie est consommee : sans cela les trois manches d'une partie
            // pourraient etre les saisons 1, 2 et 3 de la meme oeuvre, soit trois fois la
            // meme reponse depuis que les titres d'une serie sont equivalents.
            used.add(workId);
            used.addAll(workRepository.findIdsBySeriesIdIn(List.of(work.seriesKey())));
            drafts.add(new Draft(ordinal, tier, work, support.serialize(handler.prepare(work, seed, memory))));
        }
        return drafts;
    }

    /**
     * Etend une liste d'oeuvres a eviter a toutes les oeuvres de leurs series.
     *
     * L'historique du puzzle quotidien retient l'oeuvre tiree ; sans cette extension, « Attack
     * on Titan » reviendrait des le lendemain sous le nom de sa saison 2, avec la meme reponse.
     */
    private Set<Long> expandToSeries(Set<Long> workIds) {
        if (workIds.isEmpty()) {
            return workIds;
        }
        List<Integer> seriesIds = workRepository.findSeriesIdsByIdIn(workIds);
        if (seriesIds.isEmpty()) {
            return workIds;
        }
        Set<Long> expanded = new HashSet<>(workIds);
        expanded.addAll(workRepository.findIdsBySeriesIdIn(seriesIds));
        return expanded;
    }

    /**
     * Tire une oeuvre du palier demande, ou a defaut d'un autre palier.
     *
     * @return {@code null} si aucun candidat n'existe
     */
    private Long pickWork(GameModeHandler handler, WorkType theme, Difficulty tier,
                          long seed, Set<Long> used, Set<Long> avoid) {
        List<Difficulty> order = new ArrayList<>();
        order.add(tier);
        for (Difficulty other : Difficulty.values()) {
            if (other != tier) {
                order.add(other);
            }
        }

        for (Difficulty candidate : order) {
            List<Long> pool = new ArrayList<>(handler.eligibleWorkIds(theme, candidate));
            pool.removeAll(used);
            pool.removeAll(avoid);
            if (pool.isEmpty()) {
                continue;
            }
            if (candidate != tier) {
                log.debug("Palier {} vide pour {}, repli sur {}", tier, handler.mode(), candidate);
            }
            // Tri prealable : l'ordre renvoye par la base n'est pas garanti, le tirage doit l'etre.
            Collections.sort(pool);
            return pool.get(new Random(seed).nextInt(pool.size()));
        }
        return null;
    }
}
