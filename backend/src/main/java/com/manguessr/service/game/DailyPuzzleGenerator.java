package com.manguessr.service.game;

import com.manguessr.model.entity.DailyPuzzle;
import com.manguessr.model.entity.PuzzleRound;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.DailyPuzzleRepository;
import com.manguessr.service.game.mode.GameModeHandler;
import com.manguessr.service.media.MediaToken;
import com.manguessr.service.media.MediaWarmupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Genere et fige les puzzles quotidiens.
 *
 * Idempotent : un puzzle deja genere n'est jamais recalcule, meme si le catalogue a change.
 * Une oeuvre ne revient pas dans le meme mode et le meme univers avant
 * {@code app.daily.non-repeat-days} jours.
 */
@Service
public class DailyPuzzleGenerator {

    private static final Logger log = LoggerFactory.getLogger(DailyPuzzleGenerator.class);

    private final Map<GameMode, GameModeHandler> handlers = new EnumMap<>(GameMode.class);
    private final DailyPuzzleRepository puzzleRepository;
    private final RoundDrafter drafter;
    private final MediaWarmupService warmupService;
    private final TransactionTemplate requiresNew;
    private final int nonRepeatDays;

    /**
     * Serialise les creations. Une seule instance tourne sur le Pi : un verrou memoire suffit,
     * et la contrainte d'unicite en base reste le garde-fou ultime.
     */
    private final Object creationLock = new Object();

    public DailyPuzzleGenerator(List<GameModeHandler> modeHandlers,
                                DailyPuzzleRepository puzzleRepository,
                                RoundDrafter drafter,
                                MediaWarmupService warmupService,
                                PlatformTransactionManager transactionManager,
                                @Value("${app.daily.non-repeat-days:180}") int nonRepeatDays) {
        for (GameModeHandler handler : modeHandlers) {
            handlers.put(handler.mode(), handler);
        }
        this.puzzleRepository = puzzleRepository;
        this.drafter = drafter;
        this.warmupService = warmupService;
        this.nonRepeatDays = nonRepeatDays;

        // Transaction propre et validee a l'interieur du verrou : dans la transaction de
        // l'appelant, le verrou serait relache avant le commit, et deux premiers joueurs
        // simultanes genereraient chacun leur puzzle.
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Garantit l'existence du puzzle. Ne le renvoie pas : l'entite creee ici appartient a une
     * autre transaction, l'appelant doit le relire dans la sienne.
     *
     * @throws IllegalStateException si le catalogue est trop mince pour ce mode
     */
    public void ensure(LocalDate date, GameMode mode, WorkType theme) {
        if (!mode.supports(theme)) {
            throw new IllegalArgumentException(
                    "Le mode " + mode + " n'existe pas en " + theme.name().toLowerCase(Locale.ROOT) + ".");
        }
        if (puzzleRepository.existsByPuzzleDateAndModeAndTheme(date, mode, theme)) {
            return;
        }
        synchronized (creationLock) {
            requiresNew.executeWithoutResult(status -> {
                if (!puzzleRepository.existsByPuzzleDateAndModeAndTheme(date, mode, theme)) {
                    create(date, mode, theme);
                }
            });
        }
    }

    /** Genere tous les puzzles d'une journee ; un mode injouable n'empeche pas les autres. */
    public void generateAll(LocalDate date) {
        for (WorkType theme : WorkType.values()) {
            for (GameMode mode : GameMode.forType(theme)) {
                try {
                    ensure(date, mode, theme);
                } catch (RuntimeException e) {
                    log.warn("Puzzle du {} non genere pour {} {} : {}", date, mode, theme, e.getMessage());
                }
            }
        }
    }

    /**
     * Prepare les images des puzzles deja generes d'une journee.
     *
     * Complement indispensable au prechauffage fait a la generation : un puzzle cree avant la
     * mise en service de cette fonction, ou apres un redemarrage entre-temps, n'aurait jamais
     * ete prechauffe. L'operation est sans effet sur un cache deja rempli — un rendu deja
     * present se lit en 4 ms.
     */
    @Transactional(readOnly = true)
    public void warmExisting(LocalDate date) {
        for (WorkType theme : WorkType.values()) {
            for (GameMode mode : GameMode.forType(theme)) {
                puzzleRepository.findWithRounds(date, mode, theme)
                        .ifPresent(puzzle -> warmMedia(mode, puzzle));
            }
        }
    }

    private void create(LocalDate date, GameMode mode, WorkType theme) {
        Set<Long> recent = new HashSet<>(puzzleRepository.findWorkIdsUsedAround(
                mode, theme, date.minusDays(nonRepeatDays), date.plusDays(nonRepeatDays), date));

        List<RoundDrafter.Draft> drafts = drafter.draft(mode, theme, seedFor(date, mode, theme), recent);

        DailyPuzzle puzzle = new DailyPuzzle(date, mode, theme);
        for (RoundDrafter.Draft draft : drafts) {
            puzzle.addRound(new PuzzleRound(draft.ordinal(), draft.difficulty(), draft.work(), draft.payload()));
        }
        puzzleRepository.save(puzzle);
        warmMedia(mode, puzzle);

        log.info("Puzzle du {} genere : {} {} ({} manches, {} oeuvres evitees)",
                date, mode, theme, drafts.size(), recent.size());
    }

    /**
     * Prepare les images du puzzle en tache de fond, des sa generation.
     *
     * Le scheduler tourne a 00:05 UTC, quand personne ne joue : le premier joueur de la journee
     * trouve alors ses images deja rendues, au lieu de payer ~1 s par image pour les suivants.
     * Purement optionnel — un echec ramene au comportement d'avant.
     */
    private void warmMedia(GameMode mode, DailyPuzzle puzzle) {
        GameModeHandler handler = handlers.get(mode);
        if (handler == null) {
            return;
        }
        List<MediaToken> tokens = new ArrayList<>();
        for (PuzzleRound round : puzzle.getRounds()) {
            tokens.addAll(handler.mediaToWarm(round.getWork(), round.getPayload()));
        }
        if (!tokens.isEmpty()) {
            warmupService.warm(tokens);
        }
    }

    /**
     * Graine stable d'un puzzle.
     *
     * Construite sur une chaine et non via {@code Objects.hash(date, mode, theme)} : le
     * hashCode d'un enum est celui de l'objet, qui change a chaque demarrage de la JVM.
     */
    static long seedFor(LocalDate date, GameMode mode, WorkType theme) {
        return (date + ":" + mode.name() + ":" + theme.name()).hashCode();
    }
}
