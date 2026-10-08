package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.ClueView;
import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.dto.WordleRowView;
import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.RoundAttempt;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.*;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Modes Anidle et Mangadle : deduire l'oeuvre du jour a partir des attributs partages
 * avec ses propres propositions.
 *
 * Le joueur propose une oeuvre existante ; le serveur renvoie une ligne de comparaison
 * coloree. Trois indices se debloquent automatiquement avec les essais : la jaquette
 * floutee apres 12, le synopsis apres 18, le personnage le plus populaire apres 21 — un
 * essai avant la fin, pour qu'il reste de quoi s'en servir.
 *
 * Bareme : 10 000 points, moins 500 par mauvaise reponse posterieure a la troisieme.
 *
 * Passe les trois premiers essais, le joueur peut sauter jusqu'au prochain indice (comme
 * AniGuessr) : les essais sautes sont consommes et comptent comme des erreurs.
 */
@Component
public class WordleModeHandler implements GameModeHandler {

    /** Palier de flou de la jaquette indice : lisible en formes, pas en texte. */
    private static final int CLUE_COVER_BLUR_STEP = 3;

    private final MediaWorkRepository workRepository;
    private final AnswerMatcher answerMatcher;
    private final ScoreCalculator scoreCalculator;
    private final WordleComparator comparator;
    private final GameProperties properties;
    private final RoundSupport support;

    public WordleModeHandler(MediaWorkRepository workRepository,
                             AnswerMatcher answerMatcher,
                             ScoreCalculator scoreCalculator,
                             WordleComparator comparator,
                             GameProperties properties,
                             RoundSupport support) {
        this.workRepository = workRepository;
        this.answerMatcher = answerMatcher;
        this.scoreCalculator = scoreCalculator;
        this.comparator = comparator;
        this.properties = properties;
        this.support = support;
    }

    @Override
    public GameMode mode() {
        return GameMode.WORDLE;
    }

    @Override
    public List<Long> eligibleWorkIds(WorkType type, Difficulty tier) {
        // Une seule manche par puzzle : le palier ne segmente pas le pool, on prend large.
        return workRepository.findWordleModePool(type);
    }

    @Override
    public RoundPayload prepare(MediaWork work, long seed) {
        return RoundPayload.empty();
    }

    @Override
    public RoundView toView(SessionRound round) {
        GameProperties.Wordle config = properties.getWordle();
        boolean closed = round.getStatus() != SessionStatus.IN_PROGRESS;
        int attempts = round.getAttemptsUsed();

        List<WordleRowView> rows = new ArrayList<>();
        for (RoundAttempt attempt : round.getAttempts()) {
            if (attempt.getGuessedWork() != null) {
                rows.add(comparator.compare(attempt.getGuessedWork(), round.getWork()));
            }
        }
        // Le dernier essai en premier : c'est celui que le joueur vient de jouer.
        rows = rows.reversed();

        List<RoundView.MediaRef> media = new ArrayList<>();
        if (attempts >= config.getCoverClueAfter() && !closed) {
            round.getWork().getImages().stream()
                    .filter(image -> image.getKind() == ImageKind.COVER)
                    .min(Comparator.comparingInt(MediaImage::getOrdinal))
                    .ifPresent(cover -> media.add(new RoundView.MediaRef("IMAGE",
                            support.imageUrl(cover.getId(), MediaTransform.BLUR, CLUE_COVER_BLUR_STEP))));
        }

        return new RoundView(
                round.getOrdinal(),
                round.getDifficulty().name(),
                round.getStatus().name(),
                attempts,
                config.getMaxAttempts(),
                round.getScore(),
                scoreCalculator.wordleScore(true, attempts + 1),
                media,
                closed ? List.of() : skipClue(attempts),
                List.of(),
                rows,
                null,
                attempts >= config.getSynopsisClueAfter() ? synopsis(round) : null,
                attempts >= config.getCharacterClueAfter() ? topCharacter(round) : null,
                support.solutionIfClosed(round));
    }

    /**
     * Prochain seuil d'indice atteignable par un saut.
     *
     * Seuls les indices qui tombent <b>avant</b> le dernier essai comptent : un saut qui
     * consommerait tous les essais fermerait la manche sans laisser jouer l'indice.
     */
    private OptionalInt nextClueThreshold(int attempts) {
        GameProperties.Wordle config = properties.getWordle();
        return IntStream.of(config.getCoverClueAfter(), config.getSynopsisClueAfter(),
                        config.getCharacterClueAfter())
                .filter(threshold -> threshold > attempts && threshold < config.getMaxAttempts())
                .min();
    }

    /** Le saut propose, avec son cout reel : la baisse du potentiel qu'il entraine. */
    private List<ClueView> skipClue(int attempts) {
        GameProperties.Wordle config = properties.getWordle();
        OptionalInt next = nextClueThreshold(attempts);
        if (attempts < config.getFreeAttempts() || next.isEmpty()) {
            return List.of();
        }
        int threshold = next.getAsInt();
        String label = threshold == config.getCoverClueAfter() ? "Couverture"
                : threshold == config.getSynopsisClueAfter() ? "Synopsis" : "Personnage";
        int cost = scoreCalculator.wordleScore(true, attempts + 1)
                - scoreCalculator.wordleScore(true, threshold + 1);
        return List.of(new ClueView(0, label, cost, false, false));
    }

    /** Saute jusqu'au prochain indice : les essais sautes sont consommes comme des erreurs. */
    @Override
    public void unlockClue(SessionRound round) {
        int attempts = round.getAttemptsUsed();
        if (attempts < properties.getWordle().getFreeAttempts()) {
            throw new IllegalStateException("Proposez d'abord "
                    + properties.getWordle().getFreeAttempts() + " titres.");
        }
        int threshold = nextClueThreshold(attempts)
                .orElseThrow(() -> new IllegalStateException("Plus aucun indice à débloquer."));
        round.setAttemptsUsed(threshold);
    }

    private String synopsis(SessionRound round) {
        String description = round.getWork().getDescription();
        if (description == null || description.isBlank()) {
            return null;
        }
        // Le synopsis cite souvent le titre, dans une langue ou une autre : on masque toutes les
        // variantes, les plus longues d'abord pour ne pas laisser un fragment d'un titre long.
        MediaWork work = round.getWork();
        List<String> titles = new ArrayList<>();
        for (String title : new String[] {work.getTitleEnglish(), work.getTitleRomaji(), work.getTitleNative()}) {
            if (title != null && !title.isBlank()) {
                titles.add(title);
            }
        }
        titles.sort(Comparator.comparingInt(String::length).reversed());

        String masked = description;
        for (String title : titles) {
            masked = masked.replaceAll("(?i)" + java.util.regex.Pattern.quote(title), "[…]");
        }
        return masked;
    }

    private String topCharacter(SessionRound round) {
        // Une apparition (Denji dans « Dandadan ») designerait une autre oeuvre.
        return round.getWork().getCharacters().stream()
                .filter(character -> !character.isGuest())
                .max(Comparator.comparing(
                        (MediaCharacter c) -> Objects.requireNonNullElse(c.getFavourites(), 0)))
                .map(MediaCharacter::getName)
                .orElse(null);
    }

    @Override
    public GuessOutcome evaluate(SessionRound round, GuessRequest request) {
        String answer = request.answer();
        if (answer == null || answer.isBlank()) {
            return GuessOutcome.rejected("Saisissez un titre.");
        }

        MediaWork guessed = answerMatcher.resolveWork(answer, round.getSession().getTheme());
        if (guessed == null) {
            // Proposition hors catalogue : sans attributs a comparer, l'essai n'a pas de sens.
            return GuessOutcome.rejected("Titre inconnu. Choisissez une suggestion de la liste.");
        }

        // Doublon au sens de la serie : proposer la saison 2 apres la saison 1 n'apprend rien
        // de plus, les deux lignes du tableau seraient identiques.
        MediaWork alreadyTried = round.getAttempts().stream()
                .map(RoundAttempt::getGuessedWork)
                .filter(Objects::nonNull)
                .filter(work -> work.sameSeriesAs(guessed))
                .findFirst()
                .orElse(null);
        if (alreadyTried != null) {
            // AniGuessr signale le doublon sans consommer d'essai : on fait pareil.
            return GuessOutcome.rejected("Vous avez déjà proposé « " + alreadyTried.displayTitle() + " ».");
        }

        int attemptsUsed = round.getAttemptsUsed() + 1;

        // Meme serie = trouve : le joueur qui repond « Attack on Titan » a reconnu l'oeuvre,
        // meme si le tirage est tombe sur la saison 2.
        if (guessed.sameSeriesAs(round.getWork())) {
            return GuessOutcome.solved(scoreCalculator.wordleScore(true, attemptsUsed))
                    .withGuessedWork(guessed);
        }

        boolean exhausted = attemptsUsed >= properties.getWordle().getMaxAttempts();
        return GuessOutcome.wrong(exhausted, 0).withGuessedWork(guessed);
    }
}
