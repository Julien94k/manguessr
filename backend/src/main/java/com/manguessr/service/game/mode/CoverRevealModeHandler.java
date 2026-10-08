package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.*;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import com.manguessr.service.media.MediaToken;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Mode Cover-deblur : la jaquette se defloute d'un cran a chaque mauvaise reponse.
 *
 * Le flou est applique <b>cote serveur</b> et le palier est fige dans un jeton signe. C'est
 * la condition pour que le mode existe : en CSS, un joueur retirerait le filtre en deux clics,
 * et ici le flou <i>est</i> le jeu.
 *
 * Bareme : 10 000 points, moins 1000 par essai rate, sur 10 essais.
 */
@Component
public class CoverRevealModeHandler implements GameModeHandler {

    private final MediaWorkRepository workRepository;
    private final AnswerMatcher answerMatcher;
    private final ScoreCalculator scoreCalculator;
    private final GameProperties properties;
    private final RoundSupport support;

    public CoverRevealModeHandler(MediaWorkRepository workRepository,
                                  AnswerMatcher answerMatcher,
                                  ScoreCalculator scoreCalculator,
                                  GameProperties properties,
                                  RoundSupport support) {
        this.workRepository = workRepository;
        this.answerMatcher = answerMatcher;
        this.scoreCalculator = scoreCalculator;
        this.properties = properties;
        this.support = support;
    }

    @Override
    public GameMode mode() {
        return GameMode.COVER_REVEAL;
    }

    @Override
    public List<Long> eligibleWorkIds(WorkType type, Difficulty tier) {
        return workRepository.findCoverModePool(type, tier);
    }

    @Override
    public RoundPayload prepare(MediaWork work, long seed) {
        MediaImage cover = work.getImages().stream()
                .filter(image -> image.getKind() == ImageKind.COVER)
                .min(Comparator.comparingInt(MediaImage::getOrdinal))
                .orElseThrow(() -> new IllegalStateException(
                        "Oeuvre sans jaquette : " + work.displayTitle()));

        return RoundPayload.images(List.of(cover.getId()), 0);
    }

    /**
     * Tous les paliers de flou de la jaquette.
     *
     * Le mode en revele un nouveau a chaque essai rate : sans prechauffage, chacun des dix
     * essais ferait attendre un rendu. Ils partagent la meme image source, seul le flou change.
     */
    @Override
    public List<MediaToken> mediaToWarm(MediaWork work, String rawPayload) {
        List<Long> imageIds = support.deserialize(rawPayload).imageIds();
        if (imageIds.isEmpty()) {
            return List.of();
        }
        return IntStream.rangeClosed(0, properties.getCoverReveal().getMaxAttempts())
                .mapToObj(step -> MediaToken.forRender(
                        MediaSource.IMAGE, imageIds.get(0), MediaTransform.BLUR, step))
                .toList();
    }

    @Override
    public RoundView toView(SessionRound round) {
        RoundPayload payload = support.deserialize(round);
        boolean closed = round.getStatus() != SessionStatus.IN_PROGRESS;

        // Une fois la manche terminee, la jaquette est revelee entierement.
        int blurStep = closed
                ? properties.getCoverReveal().getMaxAttempts()
                : round.getAttemptsUsed();

        List<RoundView.MediaRef> media = payload.imageIds().isEmpty()
                ? List.of()
                : List.of(new RoundView.MediaRef("IMAGE",
                        support.imageUrl(payload.imageIds().get(0), MediaTransform.BLUR, blurStep)));

        return new RoundView(
                round.getOrdinal(),
                round.getDifficulty().name(),
                round.getStatus().name(),
                round.getAttemptsUsed(),
                properties.getCoverReveal().getMaxAttempts(),
                round.getScore(),
                remainingMaxScore(round),
                media,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                support.solutionIfClosed(round));
    }

    /** Points encore atteignables si le joueur trouve au prochain essai. */
    private int remainingMaxScore(SessionRound round) {
        return scoreCalculator.coverRevealScore(true, round.getAttemptsUsed() + 1);
    }

    @Override
    public GuessOutcome evaluate(SessionRound round, GuessRequest request) {
        String answer = request.answer();
        if (answer == null || answer.isBlank()) {
            return GuessOutcome.rejected("Saisissez un titre.");
        }

        int attemptsUsed = round.getAttemptsUsed() + 1;

        if (answerMatcher.matchesWork(answer, round.getWork())) {
            return GuessOutcome.solved(scoreCalculator.coverRevealScore(true, attemptsUsed));
        }

        boolean exhausted = attemptsUsed >= properties.getCoverReveal().getMaxAttempts();
        return GuessOutcome.wrong(exhausted, 0);
    }
}
