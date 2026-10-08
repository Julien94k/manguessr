package com.manguessr.service.game;

import com.manguessr.config.CacheConfig;
import com.manguessr.model.dto.*;
import com.manguessr.model.entity.*;
import com.manguessr.model.enums.*;
import com.manguessr.repository.DailyPuzzleRepository;
import com.manguessr.repository.GameSessionRepository;
import com.manguessr.repository.SessionRoundRepository;
import com.manguessr.service.game.mode.DraftMemory;
import com.manguessr.service.game.mode.GameModeHandler;
import com.manguessr.service.game.mode.GuessOutcome;
import com.manguessr.service.media.MediaToken;
import com.manguessr.service.media.MediaWarmupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestre une partie : creation des manches, arbitrage des tentatives, tenue du score.
 *
 * Les regles propres a chaque mode vivent dans les {@link GameModeHandler} ; ce service ne
 * connait que le cycle de vie commun. Il porte en revanche les deux invariants de securite :
 * le score est toujours recalcule ici, et la vue renvoyee au client passe systematiquement
 * par le handler, seul habilite a decider ce qui est visible.
 */
@Service
public class GameSessionService {

    private final Map<GameMode, GameModeHandler> handlers = new EnumMap<>(GameMode.class);
    private final GameSessionRepository sessionRepository;
    private final SessionRoundRepository roundRepository;
    private final DailyPuzzleRepository puzzleRepository;
    private final DailyPuzzleGenerator puzzleGenerator;
    private final RoundDrafter drafter;
    private final ScoreCalculator scoreCalculator;
    private final MediaWarmupService warmupService;
    private final Clock clock;
    private final int unlimitedMemory;

    public GameSessionService(List<GameModeHandler> modeHandlers,
                              GameSessionRepository sessionRepository,
                              SessionRoundRepository roundRepository,
                              DailyPuzzleRepository puzzleRepository,
                              DailyPuzzleGenerator puzzleGenerator,
                              RoundDrafter drafter,
                              ScoreCalculator scoreCalculator,
                              MediaWarmupService warmupService,
                              Clock clock,
                              @Value("${app.unlimited.recent-works:30}") int unlimitedMemory) {
        for (GameModeHandler handler : modeHandlers) {
            handlers.put(handler.mode(), handler);
        }
        this.sessionRepository = sessionRepository;
        this.roundRepository = roundRepository;
        this.puzzleRepository = puzzleRepository;
        this.puzzleGenerator = puzzleGenerator;
        this.drafter = drafter;
        this.scoreCalculator = scoreCalculator;
        this.warmupService = warmupService;
        this.clock = clock;
        this.unlimitedMemory = unlimitedMemory;
    }

    private GameModeHandler handlerFor(GameMode mode) {
        GameModeHandler handler = handlers.get(mode);
        if (handler == null) {
            throw new IllegalArgumentException("Mode non implemente : " + mode);
        }
        return handler;
    }

    /**
     * Demarre une partie, ou reprend celle du jour si elle existe deja.
     *
     * Reprendre plutot que recreer est ce qui empeche de rejouer le puzzle quotidien
     * jusqu'a obtenir un bon score.
     */
    @Transactional
    public SessionView start(User user, GameMode mode, WorkType theme, boolean unlimited) {
        if (!mode.supports(theme)) {
            throw new IllegalArgumentException(
                    "Le mode " + mode + " n'existe pas en " + theme.name().toLowerCase(Locale.ROOT) + ".");
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        if (!unlimited && user != null) {
            Optional<GameSession> existing = sessionRepository
                    .findByUserAndModeAndThemeAndPlayDateAndUnlimitedFalse(user, mode, theme, today);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        GameSession session = new GameSession();
        session.setUser(user);
        session.setMode(mode);
        session.setTheme(theme);
        session.setUnlimited(unlimited);
        session.setPlayDate(today);

        if (unlimited) {
            long seed = ThreadLocalRandom.current().nextLong();
            for (RoundDrafter.Draft draft : drafter.draft(mode, theme, seed, recentMemory(user, mode, theme))) {
                session.addRound(newRound(draft.ordinal(), draft.difficulty(), draft.work(), draft.payload()));
            }
        } else {
            // Le puzzle est fige en base : tous les joueurs du jour recoivent les memes manches.
            puzzleGenerator.ensure(today, mode, theme);
            DailyPuzzle puzzle = puzzleRepository.findWithRounds(today, mode, theme)
                    .orElseThrow(() -> new IllegalStateException("Puzzle du jour introuvable."));
            session.setPuzzle(puzzle);
            for (PuzzleRound round : puzzle.getRounds()) {
                session.addRound(newRound(round.getOrdinal(), round.getDifficulty(), round.getWork(), round.getPayload()));
            }
        }

        GameSession saved = sessionRepository.save(session);
        warmMedia(saved);
        return toView(saved);
    }

    /**
     * Lance le rendu des images de la partie en tache de fond.
     *
     * Le premier appel a une image coute environ une seconde, presque entierement passee a
     * attendre le CDN d'origine ; les suivants lisent le cache disque en 4 ms. Pendant que le
     * joueur regarde la premiere image, les indices et les manches suivantes se preparent.
     * Une partie quotidienne reprise ne repasse pas par ici : ses images sont deja en cache,
     * mises la par le premier joueur du jour.
     */
    private void warmMedia(GameSession session) {
        GameModeHandler handler = handlerFor(session.getMode());
        List<MediaToken> tokens = new ArrayList<>();
        for (SessionRound round : session.getRounds()) {
            tokens.addAll(handler.mediaToWarm(round));
        }
        if (!tokens.isEmpty()) {
            warmupService.warm(tokens);
        }
    }

    /**
     * Vide le cache de jouabilite. Appele en fin d'ingestion : c'est le seul moment ou un mode
     * peut devenir jouable, ou cesser de l'etre.
     */
    @CacheEvict(value = CacheConfig.GAME_MODES, allEntries = true)
    public void invalidateModeAvailability() {
        // L'annotation fait tout le travail.
    }

    /**
     * Oeuvres a eviter dans une nouvelle partie libre : celles de ses dernieres manches.
     *
     * Une partie libre tire a neuf dans tout le vivier, donc les repetitions arrivent vite —
     * le mode Images manga ne comptait que 126 oeuvres jouables. Un joueur anonyme n'a pas
     * d'identite cote serveur : il ne peut pas avoir de memoire. Et si le vivier est trop
     * mince pour respecter la liste, {@link RoundDrafter} la reouvre plutot que de refuser
     * la partie.
     */
    private DraftMemory recentMemory(User user, GameMode mode, WorkType theme) {
        if (user == null || unlimitedMemory <= 0) {
            return DraftMemory.empty();
        }
        return handlers.get(mode).recall(roundRepository.findRecentUnlimitedRounds(
                user, mode, theme, PageRequest.of(0, unlimitedMemory)));
    }

    private SessionRound newRound(int ordinal, Difficulty difficulty, MediaWork work, String payload) {
        SessionRound round = new SessionRound();
        round.setOrdinal(ordinal);
        round.setDifficulty(difficulty);
        round.setWork(work);
        round.setPayload(payload);
        return round;
    }

    @Transactional(readOnly = true)
    public SessionView get(Long sessionId, User user) {
        return toView(loadOwned(sessionId, user));
    }

    /** Enregistre une tentative et met a jour l'etat de la manche et de la partie. */
    @Transactional
    public GuessResponse guess(Long sessionId, User user, GuessRequest request) {
        GameSession session = loadOwned(sessionId, user);
        SessionRound round = openRound(session, request.roundOrdinal());
        GameModeHandler handler = handlerFor(session.getMode());

        GuessOutcome outcome = handler.evaluate(round, request);

        // Une tentative rejetee (doublon, titre inconnu) ne consomme pas d'essai.
        if (outcome.isRejected()) {
            return new GuessResponse(false, outcome.message(),
                    handler.toView(round), toView(session));
        }

        round.setAttemptsUsed(round.getAttemptsUsed() + 1);
        round.addAttempt(new RoundAttempt(
                round.getAttemptsUsed(), request.answer(), outcome.correct(), outcome.guessedWork()));

        if (outcome.correct()) {
            round.setStatus(SessionStatus.SOLVED);
            round.setScore(outcome.score());
        } else if (outcome.exhausted()) {
            round.setStatus(SessionStatus.FAILED);
            round.setScore(outcome.score());
        }

        finalizeIfComplete(session);
        sessionRepository.save(session);

        return new GuessResponse(outcome.correct(), outcome.message(),
                handler.toView(round), toView(session));
    }

    /** Debloque l'indice suivant d'une manche, ce qui reduit son score maximum. */
    @Transactional
    public RoundView unlockClue(Long sessionId, User user, int roundOrdinal) {
        GameSession session = loadOwned(sessionId, user);
        SessionRound round = openRound(session, roundOrdinal);
        GameModeHandler handler = handlerFor(session.getMode());

        handler.unlockClue(round);
        sessionRepository.save(session);

        return handler.toView(round);
    }

    /** Passe une manche : elle se ferme a zero point. */
    @Transactional
    public SessionView skip(Long sessionId, User user, int roundOrdinal) {
        GameSession session = loadOwned(sessionId, user);
        SessionRound round = openRound(session, roundOrdinal);

        round.setStatus(SessionStatus.SKIPPED);
        round.setScore(0);

        finalizeIfComplete(session);
        return toView(sessionRepository.save(session));
    }

    private void finalizeIfComplete(GameSession session) {
        session.recomputeTotalScore();
        if (session.allRoundsClosed() && session.getStatus() == SessionStatus.IN_PROGRESS) {
            session.setStatus(SessionStatus.FINISHED);
            session.setFinishedAt(Instant.now(clock));
        }
    }

    private SessionRound openRound(GameSession session, int ordinal) {
        SessionRound round = session.getRounds().stream()
                .filter(candidate -> candidate.getOrdinal() == ordinal)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Manche inconnue : " + ordinal));

        if (!round.isOpen()) {
            throw new IllegalStateException("Cette manche est déjà terminée.");
        }
        return round;
    }

    /**
     * Charge une partie en verifiant qu'elle appartient bien au demandeur.
     *
     * Sans ce controle, n'importe qui pourrait jouer — ou consulter — la partie d'un autre
     * en changeant l'identifiant dans l'URL.
     */
    private GameSession loadOwned(Long sessionId, User user) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Partie introuvable."));

        Long ownerId = session.getUser() == null ? null : session.getUser().getId();
        Long requesterId = user == null ? null : user.getId();

        if (!Objects.equals(ownerId, requesterId)) {
            throw new IllegalArgumentException("Partie introuvable.");
        }
        return session;
    }

    public SessionView toView(GameSession session) {
        GameModeHandler handler = handlerFor(session.getMode());

        List<RoundView> rounds = session.getRounds().stream()
                .sorted(Comparator.comparingInt(SessionRound::getOrdinal))
                .map(handler::toView)
                .toList();

        return new SessionView(
                session.getId(),
                session.getMode().name(),
                session.getTheme().name(),
                session.isUnlimited(),
                session.getPlayDate().toString(),
                session.getStatus().name(),
                session.recomputeTotalScore(),
                scoreCalculator.maxScoreFor(session.getMode()) * session.getRounds().size(),
                rounds);
    }

    /** Modes proposes dans un univers, avec leur jouabilite reelle au vu du catalogue. */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.GAME_MODES, key = "#theme")
    public List<GameModeView> availableModes(WorkType theme) {
        List<GameModeView> views = new ArrayList<>();

        for (GameMode mode : GameMode.forType(theme)) {
            GameModeHandler handler = handlers.get(mode);
            boolean playable = handler != null && Arrays.stream(Difficulty.values())
                    .anyMatch(tier -> !handler.eligibleWorkIds(theme, tier).isEmpty());

            views.add(new GameModeView(
                    mode.name(),
                    GameModeCatalog.title(mode, theme),
                    GameModeCatalog.description(mode, theme),
                    mode.getRoundsPerPuzzle(),
                    scoreCalculator.maxScoreFor(mode),
                    scoreCalculator.maxAttemptsFor(mode),
                    playable));
        }
        // Copie immuable : la liste est partagee par tous les appelants via le cache.
        return List.copyOf(views);
    }
}
