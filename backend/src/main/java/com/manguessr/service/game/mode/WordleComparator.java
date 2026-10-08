package com.manguessr.service.game.mode;

import com.manguessr.model.dto.WordleCellView;
import com.manguessr.model.dto.WordleRowView;
import com.manguessr.model.entity.MediaCredit;
import com.manguessr.model.entity.MediaTag;
import com.manguessr.model.entity.MediaWork;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Construit une ligne de comparaison entre l'oeuvre proposee et l'oeuvre cible.
 *
 * Code couleur repris d'AniGuessr :
 * <ul>
 *   <li><b>vert</b> (MATCH) : attribut commun aux deux oeuvres</li>
 *   <li><b>jaune</b> (PARTIAL) : tag commun, mais secondaire chez la cible</li>
 *   <li><b>rouge</b> (MISS) : attribut absent de la cible</li>
 * </ul>
 * Sur l'annee et le score, une fleche indique le sens de l'ecart.
 */
@Component
public class WordleComparator {

    /** Au-dela de ce rang, un tag est juge representatif de l'oeuvre. AniList note de 0 a 100. */
    private static final int PRIMARY_TAG_RANK = 60;
    /** Nombre de tags affiches : au-dela la table devient illisible. */
    private static final int MAX_TAGS = 5;

    private static final String MATCH = "MATCH";
    private static final String PARTIAL = "PARTIAL";
    private static final String MISS = "MISS";

    public WordleRowView compare(MediaWork guess, MediaWork target) {
        boolean correct = guess.getId().equals(target.getId());

        return new WordleRowView(
                guess.displayTitle(),
                correct,
                compareNumber(guess.getYear(), target.getYear()),
                compareCredits(guess, target),
                compareText(guess.getSource(), target.getSource()),
                compareNumber(guess.getAverageScore(), target.getAverageScore()),
                compareGenres(guess, target),
                compareTags(guess, target));
    }

    /**
     * Compare une valeur numerique et indique le sens de l'ecart.
     *
     * « UP » signifie que la cible est <b>plus grande</b> que la proposition : c'est la
     * lecture attendue par le joueur, qui cherche ou aller ensuite.
     */
    private WordleCellView compareNumber(Integer guessValue, Integer targetValue) {
        if (guessValue == null) {
            return new WordleCellView("?", MISS, null);
        }
        String display = String.valueOf(guessValue);

        if (targetValue == null) {
            return new WordleCellView(display, MISS, null);
        }
        if (guessValue.equals(targetValue)) {
            return new WordleCellView(display, MATCH, null);
        }
        return new WordleCellView(display, MISS, targetValue > guessValue ? "UP" : "DOWN");
    }

    private WordleCellView compareText(String guessValue, String targetValue) {
        if (guessValue == null || guessValue.isBlank()) {
            return new WordleCellView("?", MISS, null);
        }
        boolean match = guessValue.equalsIgnoreCase(targetValue);
        return new WordleCellView(humanize(guessValue), match ? MATCH : MISS, null);
    }

    private List<WordleCellView> compareCredits(MediaWork guess, MediaWork target) {
        Set<String> targetNames = new HashSet<>();
        for (MediaCredit credit : target.getCredits()) {
            targetNames.add(credit.getName().toLowerCase(Locale.ROOT));
        }

        List<WordleCellView> cells = new ArrayList<>();
        for (MediaCredit credit : guess.getCredits()) {
            boolean match = targetNames.contains(credit.getName().toLowerCase(Locale.ROOT));
            cells.add(new WordleCellView(credit.getName(), match ? MATCH : MISS, null));
        }

        if (cells.isEmpty()) {
            cells.add(new WordleCellView("?", MISS, null));
        }
        return cells;
    }

    private List<WordleCellView> compareGenres(MediaWork guess, MediaWork target) {
        Set<String> targetGenres = new HashSet<>();
        for (String genre : target.getGenres()) {
            targetGenres.add(genre.toLowerCase(Locale.ROOT));
        }

        List<WordleCellView> cells = new ArrayList<>();
        for (String genre : guess.getGenres()) {
            boolean match = targetGenres.contains(genre.toLowerCase(Locale.ROOT));
            cells.add(new WordleCellView(genre, match ? MATCH : MISS, null));
        }

        if (cells.isEmpty()) {
            cells.add(new WordleCellView("?", MISS, null));
        }
        return cells;
    }

    /**
     * Compare les tags les plus representatifs de la proposition.
     *
     * Un tag partage mais secondaire chez la cible passe en jaune : c'est l'information la
     * plus fine de la table, elle dit « bonne piste, mais ce n'est pas ce qui la definit ».
     */
    private List<WordleCellView> compareTags(MediaWork guess, MediaWork target) {
        Map<String, Integer> targetRanks = new HashMap<>();
        for (MediaTag tag : target.getTags()) {
            if (!tag.isSpoiler()) {
                targetRanks.put(tag.getName().toLowerCase(Locale.ROOT),
                        Objects.requireNonNullElse(tag.getRank(), 0));
            }
        }

        List<MediaTag> topTags = guess.getTags().stream()
                .filter(tag -> !tag.isSpoiler())
                .sorted(Comparator.comparing(
                        (MediaTag tag) -> Objects.requireNonNullElse(tag.getRank(), 0)).reversed())
                .limit(MAX_TAGS)
                .toList();

        List<WordleCellView> cells = new ArrayList<>();
        for (MediaTag tag : topTags) {
            Integer targetRank = targetRanks.get(tag.getName().toLowerCase(Locale.ROOT));
            String status;
            if (targetRank == null) {
                status = MISS;
            } else if (targetRank >= PRIMARY_TAG_RANK) {
                status = MATCH;
            } else {
                status = PARTIAL;
            }
            cells.add(new WordleCellView(tag.getName(), status, null));
        }

        if (cells.isEmpty()) {
            cells.add(new WordleCellView("?", MISS, null));
        }
        return cells;
    }

    /** « LIGHT_NOVEL » devient « Light novel ». */
    private String humanize(String value) {
        String spaced = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
