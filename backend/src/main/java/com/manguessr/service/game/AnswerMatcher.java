package com.manguessr.service.game;

import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaTitle;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaTitleRepository;
import com.manguessr.util.TextNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifie qu'une reponse de joueur designe bien l'oeuvre attendue.
 *
 * La comparaison se fait sur la forme normalisee, jamais sur le texte brut : un titre
 * possede plusieurs graphies officielles (romaji, anglais, natif) et de nombreux synonymes,
 * tous acceptes. L'index est construit a l'ingestion par le meme {@link TextNormalizer},
 * ce qui garantit que les deux cotes ne peuvent pas diverger.
 *
 * La reponse attendue n'est jamais transmise au client : c'est ce service qui tranche,
 * cote serveur, a chaque essai.
 */
@Service
public class AnswerMatcher {

    /**
     * Tolerance aux fautes de frappe. Reste a 1 volontairement : l'autocompletion propose
     * deja les titres exacts, et une tolerance plus large ferait accepter des titres voisins
     * comme « Naruto » pour « Naruto Shippuden ».
     */
    private static final int MAX_TYPO_DISTANCE = 1;

    /** En deca de cette longueur, une distance de 1 changerait le sens du titre. */
    private static final int MIN_LENGTH_FOR_TYPO_TOLERANCE = 6;

    private final MediaTitleRepository titleRepository;
    private final MediaCharacterRepository characterRepository;

    public AnswerMatcher(MediaTitleRepository titleRepository,
                         MediaCharacterRepository characterRepository) {
        this.titleRepository = titleRepository;
        this.characterRepository = characterRepository;
    }

    /**
     * La reponse designe-t-elle l'oeuvre attendue ?
     *
     * Les titres de <b>toute sa serie</b> sont acceptes : saisons, parties et suites sont des
     * oeuvres distinctes chez AniList, et un joueur qui reconnait « Attack on Titan » ne doit
     * pas perdre parce que le tirage est tombe sur la saison 2. Voir {@code SeriesGrouper}.
     *
     * @param answer texte saisi par le joueur
     * @param expected oeuvre a deviner
     */
    @Transactional(readOnly = true)
    public boolean matchesWork(String answer, MediaWork expected) {
        String normalized = TextNormalizer.normalize(answer);
        if (normalized.isEmpty()) {
            return false;
        }

        return matchesAny(normalized, acceptedForms(expected));
    }

    /**
     * Formes normalisees valant reponse pour une oeuvre.
     *
     * Les titres de l'oeuvre elle-meme sont toujours joints au resultat de la requete : une
     * oeuvre encore sans serie (catalogue anterieur au regroupement) reste devinable.
     */
    private Set<String> acceptedForms(MediaWork expected) {
        Set<String> forms = expected.getTitles().stream()
                .map(MediaTitle::getNormalized)
                .collect(Collectors.toCollection(HashSet::new));
        if (expected.getSeriesId() != null) {
            forms.addAll(titleRepository.findNormalizedBySeriesId(expected.getSeriesId()));
        }
        return forms;
    }

    /**
     * La reponse designe-t-elle le personnage attendu ?
     *
     * Les surnoms et l'ordre nom/prenom sont acceptes : AniList fournit ces variantes
     * dans {@code name.alternative}, qu'on normalise a la volee.
     */
    @Transactional(readOnly = true)
    public boolean matchesCharacter(String answer, MediaCharacter expected) {
        String normalized = TextNormalizer.normalize(answer);
        if (normalized.isEmpty()) {
            return false;
        }

        Set<String> acceptedForms = expected.getAlternativeNames().stream()
                .map(TextNormalizer::normalize)
                .filter(form -> !form.isEmpty())
                .collect(Collectors.toSet());
        acceptedForms.add(expected.getNormalizedName());

        // « Aioi, Yuuko » et « Yuuko Aioi » designent la meme personne : on tente aussi
        // la forme inversee, qu'AniList ne fournit pas toujours dans les alternatives.
        acceptedForms.add(swapCommaSeparatedName(expected.getName()));
        acceptedForms.remove("");

        return matchesAny(normalized, acceptedForms);
    }

    /**
     * Resout une reponse libre vers une oeuvre du catalogue, pour le mode Wordle : le joueur
     * propose une oeuvre existante et on la compare a la cible.
     *
     * @return l'oeuvre designee, ou {@code null} si la saisie ne correspond a rien
     */
    @Transactional(readOnly = true)
    public MediaWork resolveWork(String answer, com.manguessr.model.enums.WorkType type) {
        String normalized = TextNormalizer.normalize(answer);
        if (normalized.isEmpty()) {
            return null;
        }

        return titleRepository.findByNormalizedAndType(normalized, type).stream()
                .findFirst()
                .map(MediaTitle::getWork)
                .orElse(null);
    }

    /** Resout un nom saisi vers un personnage du catalogue. */
    @Transactional(readOnly = true)
    public MediaCharacter resolveCharacter(String answer) {
        String normalized = TextNormalizer.normalize(answer);
        if (normalized.isEmpty()) {
            return null;
        }
        return characterRepository.findByNormalizedName(normalized).stream().findFirst().orElse(null);
    }

    /**
     * Correspondance exacte d'abord, puis tolerance a une faute de frappe.
     *
     * La tolerance ne s'applique qu'aux formes assez longues : sur « K On », une distance
     * de 1 suffirait a confondre des titres distincts.
     */
    private boolean matchesAny(String normalizedAnswer, Set<String> acceptedForms) {
        if (acceptedForms.contains(normalizedAnswer)) {
            return true;
        }

        if (normalizedAnswer.length() < MIN_LENGTH_FOR_TYPO_TOLERANCE) {
            return false;
        }

        return acceptedForms.stream()
                .filter(form -> form.length() >= MIN_LENGTH_FOR_TYPO_TOLERANCE)
                .anyMatch(form -> TextNormalizer.boundedLevenshtein(
                        normalizedAnswer, form, MAX_TYPO_DISTANCE) <= MAX_TYPO_DISTANCE);
    }

    /** « Aioi, Yuuko » devient la forme normalisee de « Yuuko Aioi ». */
    private String swapCommaSeparatedName(String name) {
        int comma = name.indexOf(',');
        if (comma < 0) {
            return "";
        }
        String family = name.substring(0, comma).trim();
        String given = name.substring(comma + 1).trim();
        if (family.isEmpty() || given.isEmpty()) {
            return "";
        }
        return TextNormalizer.normalize(given + " " + family);
    }
}
