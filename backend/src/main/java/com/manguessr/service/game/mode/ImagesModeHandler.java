package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.ClueView;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Random;

/**
 * Mode Images : trois visuels de plus en plus parlants, puis un indice sur les initiales.
 *
 * Cote anime les visuels sont des vignettes d'episodes ; cote manga, des pages interieures de
 * chapitres MangaDex. Les deux sont servis tels quels : ni l'un ni l'autre ne porte le titre.
 * Les couvertures de volumes, utilisees auparavant cote manga, le portaient — meme recadrees,
 * il restait lisible sur environ un tirage sur trois.
 *
 * Bareme : 10 000 points, moins 2500 par indice debloque. La premiere image est offerte.
 */
@Component
public class ImagesModeHandler implements GameModeHandler {

    /** Trois images par manche, comme AniGuessr. */
    private static final int IMAGES_PER_ROUND = 3;
    /** Indice titre : le dernier, le plus couteux en information. */
    private static final int TITLE_CLUE_INDEX = 3;

    private final MediaWorkRepository workRepository;
    private final AnswerMatcher answerMatcher;
    private final ScoreCalculator scoreCalculator;
    private final GameProperties properties;
    private final RoundSupport support;

    public ImagesModeHandler(MediaWorkRepository workRepository,
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
        return GameMode.IMAGES;
    }

    /** La nature d'image differe selon l'univers : vignettes d'episodes ou couvertures de volumes. */
    public static ImageKind imageKindFor(WorkType type) {
        return type == WorkType.ANIME ? ImageKind.EPISODE_THUMB : ImageKind.CHAPTER_PAGE;
    }

    @Override
    public List<Long> eligibleWorkIds(WorkType type, Difficulty tier) {
        return workRepository.findImageModePool(type, tier, imageKindFor(type), IMAGES_PER_ROUND);
    }

    @Override
    public RoundPayload prepare(MediaWork work, long seed) {
        ImageKind kind = imageKindFor(work.getType());

        List<MediaImage> candidates = new ArrayList<>(work.getImages().stream()
                .filter(image -> image.getKind() == kind)
                .sorted(Comparator.comparingInt(MediaImage::getOrdinal))
                .toList());

        if (candidates.size() < IMAGES_PER_ROUND) {
            throw new IllegalStateException(
                    "Oeuvre sans assez d'images pour le mode Images : " + work.displayTitle());
        }

        Random random = new Random(seed);
        Collections.shuffle(candidates, random);

        List<Long> chosen = candidates.stream()
                .limit(IMAGES_PER_ROUND)
                .map(MediaImage::getId)
                .toList();

        return RoundPayload.images(chosen, random.nextInt(Integer.MAX_VALUE));
    }

    /**
     * Jeton de l'image de rang {@code index}.
     *
     * Une seule source de verite pour l'affichage et le prechauffage : si les deux choisissaient
     * leur traitement separement, le prechauffage remplirait le cache avec des images que
     * personne ne demanderait.
     *
     * Parties anterieures au passage aux pages de chapitres : leurs couvertures de volumes
     * restent recadrees, sinon le titre imprime apparaitrait en entier. La graine varie par
     * image, sinon les trois visuels montreraient la meme zone.
     */
    private MediaToken imageToken(RoundPayload payload, Map<Long, ImageKind> kinds, int index) {
        long imageId = payload.imageIds().get(index);
        return kinds.get(imageId) == ImageKind.VOLUME_COVER
                ? MediaToken.forRender(MediaSource.IMAGE, imageId, MediaTransform.CROP, payload.cropSeed() + index)
                : MediaToken.forRender(MediaSource.IMAGE, imageId, MediaTransform.RAW, 0);
    }

    /** Les trois images de la manche, indices encore verrouilles compris. */
    @Override
    public List<MediaToken> mediaToWarm(MediaWork work, String rawPayload) {
        RoundPayload payload = support.deserialize(rawPayload);
        Map<Long, ImageKind> kinds = imageKinds(work);
        return IntStream.range(0, payload.imageIds().size())
                .mapToObj(index -> imageToken(payload, kinds, index))
                .toList();
    }

    private Map<Long, ImageKind> imageKinds(MediaWork work) {
        return work.getImages().stream()
                .collect(Collectors.toMap(MediaImage::getId, MediaImage::getKind, (a, b) -> a));
    }

    @Override
    public RoundView toView(SessionRound round) {
        RoundPayload payload = support.deserialize(round);
        MediaWork work = round.getWork();
        boolean closed = round.getStatus() != SessionStatus.IN_PROGRESS;

        Map<Long, ImageKind> kinds = imageKinds(work);

        // Une image debloquee de plus par indice, la premiere etant offerte.
        int visibleImages = Math.min(payload.imageIds().size(), 1 + round.getCluesUnlocked());

        List<RoundView.MediaRef> media = new ArrayList<>();
        for (int i = 0; i < visibleImages; i++) {
            MediaToken token = imageToken(payload, kinds, i);
            media.add(new RoundView.MediaRef("IMAGE",
                    support.imageUrl(token.id(), token.transform(), token.parameter())));
        }

        boolean titleClueUnlocked = round.getCluesUnlocked() >= TITLE_CLUE_INDEX;

        return new RoundView(
                round.getOrdinal(),
                round.getDifficulty().name(),
                round.getStatus().name(),
                round.getAttemptsUsed(),
                scoreCalculator.maxAttemptsFor(mode()),
                round.getScore(),
                remainingMaxScore(round),
                media,
                clues(round),
                List.of(),
                List.of(),
                titleClueUnlocked || closed ? support.maskTitle(work.displayTitle()) : null,
                null,
                null,
                support.solutionIfClosed(round));
    }

    private List<ClueView> clues(SessionRound round) {
        GameProperties.Images config = properties.getImages();
        List<ClueView> clues = new ArrayList<>();

        clues.add(new ClueView(0, "Image 1", 0, true, true));
        clues.add(new ClueView(1, "Image 2", config.getClueCost(), round.getCluesUnlocked() >= 1, false));
        clues.add(new ClueView(2, "Image 3", config.getClueCost(), round.getCluesUnlocked() >= 2, false));
        clues.add(new ClueView(TITLE_CLUE_INDEX, "Initiales du titre", config.getClueCost(),
                round.getCluesUnlocked() >= TITLE_CLUE_INDEX, false));

        return clues;
    }

    /** Points encore atteignables compte tenu des indices deja debloques. */
    private int remainingMaxScore(SessionRound round) {
        return scoreCalculator.imagesScore(true, round.getCluesUnlocked());
    }

    @Override
    public void unlockClue(SessionRound round) {
        if (round.getCluesUnlocked() >= TITLE_CLUE_INDEX) {
            throw new IllegalStateException("Tous les indices sont déjà débloqués.");
        }
        round.setCluesUnlocked(round.getCluesUnlocked() + 1);
    }

    @Override
    public GuessOutcome evaluate(SessionRound round, GuessRequest request) {
        String answer = request.answer();
        if (answer == null || answer.isBlank()) {
            // Reponse vide : AniGuessr l'autorise et la traite comme un abandon de manche.
            return GuessOutcome.wrong(true, 0);
        }

        if (answerMatcher.matchesWork(answer, round.getWork())) {
            return GuessOutcome.solved(scoreCalculator.imagesScore(true, round.getCluesUnlocked()));
        }

        // Un seul essai par manche dans ce mode : une erreur la termine.
        return GuessOutcome.wrong(true, 0);
    }
}
