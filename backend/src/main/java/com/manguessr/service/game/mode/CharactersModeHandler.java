package com.manguessr.service.game.mode;

import com.manguessr.config.GameProperties;
import com.manguessr.model.dto.CharacterSlotView;
import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.*;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import com.manguessr.service.media.MediaToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Mode Personnages : quatre portraits, nom du personnage et titre de l'oeuvre a trouver.
 *
 * Les quatre portraits proviennent de <b>series</b> differentes, comme sur AniGuessr : sinon
 * reconnaitre un seul personnage donnerait les quatre titres d'un coup, soit 2000 points
 * offerts. Chaque reponse de titre est donc comparee a l'oeuvre de <i>son</i> personnage,
 * et non a une oeuvre unique de manche.
 *
 * Deux autres particularites : les quatre reponses sont soumises en une fois, et il n'y a
 * pas de manche reussie ou ratee — le score est la somme des bonnes reponses, 2000 points
 * par personnage et 500 par titre.
 *
 * « Oeuvres differentes » ne suffit pas a garantir « personnes differentes » : les saisons
 * d'une serie sont des oeuvres distinctes chez AniList et partagent leurs personnages. Le
 * tirage ecarte donc aussi les doublons de personne — sur toute la partie, pas seulement la
 * manche — et les fiches generiques. Deux portraits de la meme franchise seraient de meme un
 * cadeau : le meme titre repondrait aux deux, puisqu'une serie entiere vaut une reponse. En partie libre, il evite en plus les personnes et les
 * oeuvres des parties recentes du joueur (voir {@link DraftMemory}).
 */
@Component
public class CharactersModeHandler implements GameModeHandler {

    /** Une seule oeuvre suffit a entrer dans le pool : on n'y prend qu'un personnage. */
    private static final long MIN_CHARACTERS_PER_WORK = 1;

    /** Sentinelle : aucun identifiant AniList n'est negatif, et « not in () » est invalide. */
    private static final int NO_SUCH_ANILIST_ID = -1;

    /** Les fiches generiques ne changent qu'a une ingestion : inutile de les relire sans cesse. */
    private static final long GENERIC_CACHE_TTL_NANOS = Duration.ofMinutes(10).toNanos();

    private volatile List<Integer> genericIds;
    private volatile long genericIdsExpiry;

    private final MediaWorkRepository workRepository;
    private final MediaCharacterRepository characterRepository;
    private final AnswerMatcher answerMatcher;
    private final ScoreCalculator scoreCalculator;
    private final GameProperties properties;
    private final RoundSupport support;

    public CharactersModeHandler(MediaWorkRepository workRepository,
                                 MediaCharacterRepository characterRepository,
                                 AnswerMatcher answerMatcher,
                                 ScoreCalculator scoreCalculator,
                                 GameProperties properties,
                                 RoundSupport support) {
        this.workRepository = workRepository;
        this.characterRepository = characterRepository;
        this.answerMatcher = answerMatcher;
        this.scoreCalculator = scoreCalculator;
        this.properties = properties;
        this.support = support;
    }

    @Override
    public GameMode mode() {
        return GameMode.CHARACTERS;
    }

    private int slotsPerRound() {
        return properties.getCharacters().getPerRound();
    }

    @Override
    public List<Long> eligibleWorkIds(WorkType type, Difficulty tier) {
        return workRepository.findCharacterModePool(
                type, tier, MIN_CHARACTERS_PER_WORK, genericCharacterIds());
    }

    /**
     * Fiches AniList rattachees a trop d'oeuvres pour etre des personnages, avec la sentinelle.
     *
     * L'agregat parcourt toute la table : il est mis en cache dix minutes, faute de quoi il
     * serait rejoue a chaque tirage et a chaque appel de {@code /api/games}.
     */
    private List<Integer> genericCharacterIds() {
        long now = System.nanoTime();
        if (genericIds == null || now - genericIdsExpiry >= 0) {
            List<Integer> found = new ArrayList<>(characterRepository.findAnilistIdsSharedByManyWorks(
                    properties.getCharacters().getGenericMinWorks()));
            found.add(NO_SUCH_ANILIST_ID);
            genericIds = List.copyOf(found);
            genericIdsExpiry = now + GENERIC_CACHE_TTL_NANOS;
        }
        return genericIds;
    }

    /**
     * Tire un personnage par oeuvre, sur quatre <b>series</b> distinctes.
     *
     * L'oeuvre passee en parametre sert d'ancre ; les trois autres sont tirees dans le meme
     * palier de difficulte, avec repli sur les autres paliers si le pool est trop mince.
     */
    @Override
    public RoundPayload prepare(MediaWork anchor, long seed) {
        return prepare(anchor, seed, DraftMemory.empty());
    }

    @Override
    public RoundPayload prepare(MediaWork anchor, long seed, DraftMemory memory) {
        Random random = new Random(seed);
        List<Integer> generic = genericCharacterIds();

        List<Long> chosenCharacters = new ArrayList<>();
        Set<Long> usedWorks = new LinkedHashSet<>();
        Set<Integer> usedSeries = new HashSet<>();

        MediaCharacter anchorCharacter = pickCharacter(anchor, random, generic, memory);
        if (anchorCharacter == null) {
            // L'ancre est imposee par le tirage des oeuvres : une personne deja montree dans la
            // partie vaut mieux qu'une partie refusee (oeuvre a personnage unique, deja vu).
            anchorCharacter = pickCharacter(anchor, random, generic, DraftMemory.empty());
        }
        if (anchorCharacter == null) {
            throw new IllegalStateException(
                    "Oeuvre sans personnage illustre exploitable : " + anchor.displayTitle());
        }
        chosenCharacters.add(anchorCharacter.getId());
        usedWorks.add(anchor.getId());
        rememberSeries(usedSeries, anchor);
        memory.markShown(personKey(anchorCharacter));

        for (Long workId : companionWorkIds(anchor, random, usedWorks, generic, memory.recentWorks())) {
            if (chosenCharacters.size() >= slotsPerRound()) {
                break;
            }
            MediaWork companion = workRepository.findById(workId).orElse(null);
            // Une saison de la serie deja representee donnerait deux fois le meme titre.
            if (companion == null || usedSeries.contains(companion.seriesKey())) {
                continue;
            }
            MediaCharacter character = pickCharacter(companion, random, generic, memory);
            if (character != null) {
                chosenCharacters.add(character.getId());
                usedWorks.add(workId);
                rememberSeries(usedSeries, companion);
                memory.markShown(personKey(character));
            }
        }

        if (chosenCharacters.size() < slotsPerRound()) {
            throw new IllegalStateException(
                    "Catalogue insuffisant : il faut " + slotsPerRound()
                            + " series distinctes avec un personnage illustre.");
        }

        // Melange final : sinon l'oeuvre ancre occuperait toujours le premier portrait.
        Collections.shuffle(chosenCharacters, random);
        return RoundPayload.characters(chosenCharacters);
    }

    /**
     * Retient la serie d'une oeuvre deja representee.
     *
     * Une cle nulle (oeuvre sans identifiant AniList, cas impossible en base) n'est pas
     * retenue : elle bloquerait toutes les autres oeuvres dans le meme cas.
     */
    private static void rememberSeries(Set<Integer> usedSeries, MediaWork work) {
        if (work.seriesKey() != null) {
            usedSeries.add(work.seriesKey());
        }
    }

    /**
     * Identifiants d'oeuvres candidates, palier courant d'abord puis les autres. Les oeuvres
     * des parties recentes passent en fin de liste : evitees si possible, jamais bloquantes.
     */
    private List<Long> companionWorkIds(MediaWork anchor, Random random, Set<Long> exclude,
                                        List<Integer> generic, Set<Long> recentWorks) {
        List<Long> candidates = new ArrayList<>();

        List<Difficulty> tiers = new ArrayList<>();
        if (anchor.getDifficultyTier() != null) {
            tiers.add(anchor.getDifficultyTier());
        }
        for (Difficulty tier : Difficulty.values()) {
            if (!tiers.contains(tier)) {
                tiers.add(tier);
            }
        }

        for (Difficulty tier : tiers) {
            List<Long> pool = new ArrayList<>(workRepository.findCharacterModePool(
                    anchor.getType(), tier, MIN_CHARACTERS_PER_WORK, generic));
            pool.removeAll(exclude);
            pool.removeAll(candidates);
            Collections.shuffle(pool, random);
            candidates.addAll(pool);

            // Une marge au-dela du strict necessaire couvre les oeuvres sans portrait exploitable
            // et celles vues recemment. Collections.sort etant stable, le melange est conserve.
            long fresh = candidates.stream().filter(id -> !recentWorks.contains(id)).count();
            if (fresh >= slotsPerRound() * 3L) {
                break;
            }
        }
        candidates.sort(Comparator.comparing(recentWorks::contains));
        return candidates;
    }

    /**
     * Un personnage connu de l'oeuvre : une manche doit rester devinable.
     *
     * Quatre filtres avant le tirage. Le personnage doit etre illustre ; il ne doit pas etre une
     * simple apparition dans l'oeuvre (Denji dans « Dandadan » : la solution serait une oeuvre
     * que personne ne lui associe, voir {@code CharacterHomes}) ; il ne doit pas etre une
     * fiche generique (« Narrator », rattachee a 146 animes, sortait dans une manche sur cinq) ;
     * et il ne doit pas etre <b>la meme personne</b> qu'un portrait deja montre dans la partie —
     * Levi appartient a douze oeuvres, Ichigo a onze.
     *
     * Le tirage se fait parmi les N plus populaires, N selon le palier de l'oeuvre (voir
     * {@code app.game.characters.pick-from-top-*}) ; parmi eux, les personnes vues dans les
     * parties recentes du joueur ne sont retenues qu'a defaut d'autres. On ne descend pas plus
     * bas dans la liste pour les eviter : le portrait doit rester reconnaissable.
     */
    private MediaCharacter pickCharacter(MediaWork work, Random random,
                                         List<Integer> generic, DraftMemory memory) {
        List<MediaCharacter> illustrated = work.getCharacters().stream()
                .filter(character -> character.getImageUrl() != null)
                .filter(character -> !character.isGuest())
                .filter(character -> character.getAnilistId() == null
                        || !generic.contains(character.getAnilistId()))
                .filter(character -> !memory.shownInGame(personKey(character)))
                .sorted(Comparator.comparing(
                        (MediaCharacter c) -> Objects.requireNonNullElse(c.getFavourites(), 0)).reversed())
                .toList();

        if (illustrated.isEmpty()) {
            return null;
        }
        int top = Math.max(1, properties.getCharacters().pickFromTop(work.getDifficultyTier()));
        List<MediaCharacter> candidates = illustrated.subList(0, Math.min(top, illustrated.size()));
        List<MediaCharacter> fresh = candidates.stream()
                .filter(character -> !memory.seenRecently(personKey(character)))
                .toList();
        List<MediaCharacter> pool = fresh.isEmpty() ? candidates : fresh;
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Memoire d'un joueur pour ce mode : les oeuvres et les personnes de <b>tous</b> les
     * portraits de ses manches recentes, pas seulement l'oeuvre ancre de chaque manche.
     */
    @Override
    public DraftMemory recall(List<SessionRound> recentRounds) {
        Set<Long> works = new HashSet<>();
        List<Long> characterIds = new ArrayList<>();
        for (SessionRound round : recentRounds) {
            works.add(round.getWork().getId());
            characterIds.addAll(Objects.requireNonNullElse(support.deserialize(round).characterIds(), List.of()));
        }

        Set<String> people = new HashSet<>();
        for (MediaCharacter character : characterRepository.findAllById(characterIds)) {
            works.add(character.getWork().getId());
            people.add(personKey(character));
        }
        return DraftMemory.recent(works, people);
    }

    /** Identite d'une personne, par-dela les oeuvres ou elle apparait. */
    public static String personKey(MediaCharacter character) {
        return character.getAnilistId() != null
                ? "anilist:" + character.getAnilistId()
                : "local:" + character.getId();
    }

    /** Les quatre portraits de la manche : ils s'affichent tous d'un coup. */
    @Override
    public List<MediaToken> mediaToWarm(MediaWork work, String rawPayload) {
        return support.deserialize(rawPayload).characterIds().stream()
                .map(id -> MediaToken.forRender(MediaSource.CHARACTER, id, MediaTransform.RAW, 0))
                .toList();
    }

    @Override
    public RoundView toView(SessionRound round) {
        RoundPayload payload = support.deserialize(round);
        boolean closed = round.getStatus() != SessionStatus.IN_PROGRESS;

        List<CharacterSlotView> slots = new ArrayList<>();
        List<Long> ids = payload.characterIds();

        for (int slot = 0; slot < ids.size(); slot++) {
            MediaCharacter character = characterRepository.findById(ids.get(slot)).orElse(null);
            if (character == null) {
                continue;
            }

            slots.add(new CharacterSlotView(
                    slot,
                    support.characterUrl(character.getId()),
                    // Nom et titre ne sont reveles qu'une fois la manche close.
                    closed ? character.getName() : null,
                    closed ? character.getWork().displayTitle() : null,
                    payload.isNameFound(slot),
                    payload.isTitleFound(slot)));
        }

        return new RoundView(
                round.getOrdinal(),
                round.getDifficulty().name(),
                round.getStatus().name(),
                round.getAttemptsUsed(),
                scoreCalculator.maxAttemptsFor(mode()),
                round.getScore(),
                scoreCalculator.maxScoreFor(mode()),
                List.of(),
                List.of(),
                slots,
                List.of(),
                null,
                null,
                null,
                // Il n'y a pas d'oeuvre unique a reveler : chaque portrait porte la sienne.
                null);
    }

    @Override
    public GuessOutcome evaluate(SessionRound round, GuessRequest request) {
        RoundPayload payload = support.deserialize(round);
        List<GuessRequest.CharacterGuess> entries =
                request.entries() == null ? List.of() : request.entries();

        List<Boolean> nameFound = new ArrayList<>();
        List<Boolean> titleFound = new ArrayList<>();
        int correctNames = 0;
        int correctTitles = 0;

        for (int slot = 0; slot < payload.characterIds().size(); slot++) {
            MediaCharacter character = characterRepository.findById(payload.characterIds().get(slot))
                    .orElse(null);
            GuessRequest.CharacterGuess entry = slot < entries.size() ? entries.get(slot) : null;

            boolean nameOk = character != null && entry != null
                    && answerMatcher.matchesCharacter(nullToEmpty(entry.character()), character);

            // Chaque titre se compare a l'oeuvre de SON personnage : les quatre portraits
            // viennent d'oeuvres differentes.
            boolean titleOk = character != null && entry != null
                    && worksOf(character).stream()
                            .anyMatch(work -> answerMatcher.matchesWork(nullToEmpty(entry.title()), work));

            if (nameOk) {
                correctNames++;
            }
            if (titleOk) {
                correctTitles++;
            }
            nameFound.add(nameOk);
            titleFound.add(titleOk);
        }

        // Le detail est conserve dans le contenu de la manche : c'est lui qui alimentera
        // l'affichage de fin, la reponse brute etant vide dans ce mode.
        round.setPayload(support.serialize(payload.withResults(nameFound, titleFound)));

        int score = scoreCalculator.charactersScore(correctNames, correctTitles);
        int slots = payload.characterIds().size();
        boolean perfect = correctNames == slots && correctTitles == slots;

        if (perfect) {
            return GuessOutcome.solved(score);
        }

        // Un seul essai : la manche se ferme quel que soit le resultat.
        return new GuessOutcome(false, true, score,
                correctNames + " personnage(s) et " + correctTitles + " titre(s) trouvé(s).", null);
    }

    /**
     * Oeuvres valant reponse pour un portrait : la sienne, et toutes celles ou la meme personne
     * apparait. Le tirage prend le personnage dans son oeuvre d'origine, mais un joueur qui
     * nomme un crossover ou le personnage figure bel et bien n'a pas tort.
     */
    private List<MediaWork> worksOf(MediaCharacter character) {
        List<MediaWork> works = new ArrayList<>();
        works.add(character.getWork());
        if (character.getAnilistId() != null) {
            characterRepository.findWorksOfPerson(character.getAnilistId(), character.getWork().getType()).stream()
                    .filter(work -> !work.getId().equals(character.getWork().getId()))
                    .forEach(works::add);
        }
        return works;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
