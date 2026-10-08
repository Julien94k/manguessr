package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.ClueView;
import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaTheme;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.*;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Modes Opening et Ending : reconnaitre l'anime a son generique.
 *
 * L'audio est offert, le clip video coute 2500 des 5000 points de la manche. Le mode Ending
 * affiche en plus une jaquette floutee, comme AniGuessr : les generiques de fin sont souvent
 * plus difficiles, l'indice visuel compense.
 *
 * Deux instances de cette classe sont enregistrees, une par type de generique.
 */
public class ThemeModeHandler implements GameModeHandler {

    /** Palier de flou de la jaquette offerte en mode Ending : reconnaissable sans etre lisible. */
    private static final int ENDING_COVER_BLUR_STEP = 2;
    private static final int VIDEO_CLUE_INDEX = 1;

    private final GameMode gameMode;
    private final ThemeKind themeKind;
    private final MediaWorkRepository workRepository;
    private final AnswerMatcher answerMatcher;
    private final ScoreCalculator scoreCalculator;
    private final GameProperties properties;
    private final RoundSupport support;

    public ThemeModeHandler(GameMode gameMode,
                            ThemeKind themeKind,
                            MediaWorkRepository workRepository,
                            AnswerMatcher answerMatcher,
                            ScoreCalculator scoreCalculator,
                            GameProperties properties,
                            RoundSupport support) {
        this.gameMode = gameMode;
        this.themeKind = themeKind;
        this.workRepository = workRepository;
        this.answerMatcher = answerMatcher;
        this.scoreCalculator = scoreCalculator;
        this.properties = properties;
        this.support = support;
    }

    @Override
    public GameMode mode() {
        return gameMode;
    }

    @Override
    public List<Long> eligibleWorkIds(WorkType type, Difficulty tier) {
        if (type != WorkType.ANIME) {
            return List.of();
        }
        return workRepository.findThemeModePool(tier, themeKind);
    }

    @Override
    public RoundPayload prepare(MediaWork work, long seed) {
        List<MediaTheme> candidates = work.getThemes().stream()
                .filter(theme -> theme.getKind() == themeKind && theme.getAudioUrl() != null)
                .sorted(Comparator.comparingInt(MediaTheme::getSequence))
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "Oeuvre sans generique " + themeKind + " : " + work.displayTitle());
        }

        // Une serie a souvent plusieurs generiques : on en tire un, pour varier les manches.
        MediaTheme chosen = candidates.get(new Random(seed).nextInt(candidates.size()));
        return RoundPayload.theme(chosen.getId());
    }

    @Override
    public RoundView toView(SessionRound round) {
        RoundPayload payload = support.deserialize(round);
        boolean closed = round.getStatus() != SessionStatus.IN_PROGRESS;
        boolean videoUnlocked = round.getCluesUnlocked() >= 1;

        MediaTheme theme = round.getWork().getThemes().stream()
                .filter(candidate -> candidate.getId().equals(payload.themeId()))
                .findFirst()
                .orElse(null);

        List<RoundView.MediaRef> media = new ArrayList<>();
        if (theme != null) {
            // L'audio est toujours servi ; la video seulement si l'indice a ete paye.
            media.add(new RoundView.MediaRef("AUDIO",
                    support.themeUrl(theme.getId(), MediaSource.THEME_AUDIO)));
            if ((videoUnlocked || closed) && theme.getVideoUrl() != null) {
                media.add(new RoundView.MediaRef("VIDEO",
                        support.themeUrl(theme.getId(), MediaSource.THEME_VIDEO)));
            }
        }

        // Mode Ending : jaquette floutee offerte, comme sur AniGuessr.
        if (gameMode == GameMode.ENDING && !closed) {
            round.getWork().getImages().stream()
                    .filter(image -> image.getKind() == ImageKind.COVER)
                    .min(Comparator.comparingInt(MediaImage::getOrdinal))
                    .ifPresent(cover -> media.add(new RoundView.MediaRef("IMAGE",
                            support.imageUrl(cover.getId(), MediaTransform.BLUR, ENDING_COVER_BLUR_STEP))));
        }

        return new RoundView(
                round.getOrdinal(),
                round.getDifficulty().name(),
                round.getStatus().name(),
                round.getAttemptsUsed(),
                scoreCalculator.maxAttemptsFor(gameMode),
                round.getScore(),
                scoreCalculator.themeScore(true, videoUnlocked),
                media,
                clues(round),
                List.of(),
                List.of(),
                null,
                null,
                null,
                closed && theme != null
                        ? support.solution(round.getWork(), theme.getSongTitle(), theme.getArtist())
                        : support.solutionIfClosed(round));
    }

    private List<ClueView> clues(SessionRound round) {
        return List.of(
                new ClueView(0, "Audio", 0, true, true),
                new ClueView(VIDEO_CLUE_INDEX, "Clip vidéo",
                        properties.getTheme().getVideoClueCost(),
                        round.getCluesUnlocked() >= 1, false));
    }

    @Override
    public void unlockClue(SessionRound round) {
        if (round.getCluesUnlocked() >= 1) {
            throw new IllegalStateException("Le clip vidéo est déjà débloqué.");
        }
        round.setCluesUnlocked(1);
    }

    @Override
    public GuessOutcome evaluate(SessionRound round, GuessRequest request) {
        String answer = request.answer();
        if (answer == null || answer.isBlank()) {
            return GuessOutcome.wrong(true, 0);
        }

        if (answerMatcher.matchesWork(answer, round.getWork())) {
            return GuessOutcome.solved(scoreCalculator.themeScore(true, round.getCluesUnlocked() >= 1));
        }
        return GuessOutcome.wrong(true, 0);
    }
}
