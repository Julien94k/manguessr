package com.manguessr.service.catalog;

import com.manguessr.config.CatalogTiers;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaTheme;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.ImageKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.repository.projection.WorkAudience;
import com.manguessr.service.catalog.dto.CatalogPage;
import com.manguessr.service.game.GameSessionService;
import com.manguessr.service.catalog.dto.WorkRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alimente le catalogue local a partir des sources externes.
 *
 * <h3>Pourquoi un catalogue local</h3>
 * Aucune API tierce n'est appelee pendant une partie. AniList est plafonne a 30 req/min et
 * Jikan renvoie des 504 intermittents : dependre d'eux en jeu rendrait chaque manche lente
 * et fragile. L'ingestion tourne en tache de fond, le jeu ne lit que Postgres.
 *
 * <h3>Deroule</h3>
 * <ol>
 *   <li>AniList, pagine : metadonnees, titres, tags, credits, personnages, vignettes d'episodes</li>
 *   <li>MangaDex : correspondance des identifiants puis couvertures de volumes (mode Images manga)</li>
 *   <li>AnimeThemes : openings et endings (modes Opening / Ending)</li>
 *   <li>Calcul des paliers de difficulte par rang de popularite</li>
 * </ol>
 *
 * Chaque etape est isolee : l'echec d'une source ne fait pas perdre le travail des precedentes.
 */
@Service
public class CatalogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CatalogIngestionService.class);

    /** Le mode Images demande 3 images ; on en stocke davantage pour varier les manches. */
    private static final int VOLUME_COVERS_PER_MANGA = 6;
    /** Au-dela, la recherche par titre est bloquee cote MangaDex, pas malchanceuse. */
    private static final int MAX_CONSECUTIVE_SEARCH_FAILURES = 10;

    private final MediaCatalogProvider provider;
    private final MangaDexClient mangaDexClient;
    private final AnimeThemesClient animeThemesClient;
    private final MediaWorkRepository workRepository;
    private final CatalogPersistenceService persistenceService;
    private final AutocompleteService autocompleteService;
    private final GameSessionService sessionService;
    private final IngestionStatus status = new IngestionStatus();

    /** Une seule ingestion a la fois : les limiteurs de debit supposent un thread unique par source. */
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    private final int pageSize;
    private final int maxPagesPerType;
    private final int mangadexMappingSize;
    private final int maxMangaForTitleSearch;
    private final int maxWorksForThemes;
    private final int maxMangaForCovers;
    private final int maxMangaForPages;
    private final int chaptersPerManga;
    private final int pagesPerChapter;
    private final CatalogTiers tiers;

    public CatalogIngestionService(
            List<MediaCatalogProvider> providers,
            MangaDexClient mangaDexClient,
            AnimeThemesClient animeThemesClient,
            MediaWorkRepository workRepository,
            CatalogPersistenceService persistenceService,
            AutocompleteService autocompleteService,
            GameSessionService sessionService,
            @Value("${app.catalog.provider:anilist}") String providerName,
            @Value("${app.catalog.page-size:50}") int pageSize,
            @Value("${app.catalog.max-pages-per-type:20}") int maxPagesPerType,
            @Value("${app.catalog.mangadex.mapping-size:2000}") int mangadexMappingSize,
            @Value("${app.catalog.mangadex.max-manga-for-title-search:2000}") int maxMangaForTitleSearch,
            @Value("${app.catalog.animethemes.max-works:400}") int maxWorksForThemes,
            @Value("${app.catalog.mangadex.max-manga-for-covers:400}") int maxMangaForCovers,
            @Value("${app.catalog.mangadex.max-manga-for-pages:400}") int maxMangaForPages,
            @Value("${app.catalog.mangadex.chapters-per-manga:3}") int chaptersPerManga,
            @Value("${app.catalog.mangadex.pages-per-chapter:2}") int pagesPerChapter,
            CatalogTiers tiers) {

        this.provider = providers.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fournisseur de catalogue inconnu : '" + providerName + "'. Disponibles : "
                                + providers.stream().map(MediaCatalogProvider::name).toList()));

        this.mangaDexClient = mangaDexClient;
        this.animeThemesClient = animeThemesClient;
        this.workRepository = workRepository;
        this.persistenceService = persistenceService;
        this.autocompleteService = autocompleteService;
        this.sessionService = sessionService;
        this.pageSize = pageSize;
        this.maxPagesPerType = maxPagesPerType;
        this.mangadexMappingSize = mangadexMappingSize;
        this.maxMangaForTitleSearch = maxMangaForTitleSearch;
        this.maxWorksForThemes = maxWorksForThemes;
        this.maxMangaForCovers = maxMangaForCovers;
        this.maxMangaForPages = maxMangaForPages;
        this.chaptersPerManga = chaptersPerManga;
        this.pagesPerChapter = pagesPerChapter;
        this.tiers = tiers;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    /**
     * Lance une ingestion complete en tache de fond.
     *
     * @throws IllegalStateException si une ingestion est deja en cours
     */
    @Async
    public void ingestAsync() {
        if (!inProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Une ingestion est déjà en cours.");
        }

        status.start();
        try {
            ingestWorks(WorkType.ANIME);
            ingestWorks(WorkType.MANGA);
            ingestMangaCovers();
            ingestMangaChapterPages();
            ingestAnimeThemes();
            assignDifficultyTiers();

            // Sans cela, le front continuerait a servir l'index d'autocompletion d'avant
            // l'ingestion, et l'accueil la jouabilite des modes d'avant.
            autocompleteService.invalidate();
            sessionService.invalidateModeAvailability();

            status.log("Ingestion terminée.");
            status.finish(null);

        } catch (Exception e) {
            log.error("Ingestion interrompue : {}", e.getMessage(), e);
            status.log("ECHEC : " + e.getMessage());
            status.finish(e.getMessage());
        } finally {
            inProgress.set(false);
        }
    }

    /** Etape 1 : metadonnees depuis le fournisseur configure, puis regroupement en series. */
    private void ingestWorks(WorkType type) {
        status.log("Ingestion " + type + " via " + provider.name() + "...");
        int saved = 0;
        List<WorkRelation> relations = new ArrayList<>();

        for (int page = 1; page <= maxPagesPerType; page++) {
            CatalogPage catalogPage;
            try {
                catalogPage = provider.fetchPage(type, page, pageSize);
            } catch (CatalogHttpException e) {
                // Une page perdue ne doit pas annuler les precedentes : on arrete proprement ce type.
                status.log("ATTENTION " + type + " page " + page + " : " + e.getMessage());
                break;
            }

            if (catalogPage.works().isEmpty()) {
                break;
            }

            saved += persistenceService.saveAll(catalogPage.works());
            relations.addAll(catalogPage.relations());
            status.progress("oeuvres " + type, saved, Math.min(maxPagesPerType, catalogPage.lastPage()) * pageSize);

            if (page % 5 == 0 || !catalogPage.hasNextPage()) {
                status.log(type + " : " + saved + " oeuvres enregistrees (page " + page + ")");
            }
            if (!catalogPage.hasNextPage()) {
                break;
            }
        }

        status.log(type + " : " + saved + " oeuvres au total.");
        assignSeries(type, relations);
    }

    /**
     * Regroupe l'univers en series : saisons, parties et suites d'une meme franchise partagent
     * un identifiant de serie.
     *
     * Ne peut se faire qu'une fois l'univers entier enregistre — une relation ne compte que si
     * ses deux extremites sont au catalogue. Sans relations (source en echec), on ne touche a
     * rien : les groupes de l'ingestion precedente valent mieux qu'un catalogue degroupe.
     */
    private void assignSeries(WorkType type, List<WorkRelation> relations) {
        if (relations.isEmpty()) {
            return;
        }
        List<Integer> anilistIds = workRepository.findAnilistIdsByTypeOrderByPopularityDesc(type);
        Map<Integer, String> titles = new HashMap<>();
        for (Object[] row : workRepository.findRomajiTitlesByType(type)) {
            titles.put((Integer) row[0], (String) row[1]);
        }
        Map<Integer, Integer> seriesByWork = SeriesGrouper.group(anilistIds, relations, titles);
        int changed = persistenceService.applySeries(seriesByWork);

        long series = seriesByWork.values().stream().distinct().count();
        status.log(type + " : " + anilistIds.size() + " oeuvres regroupees en " + series
                + " series (" + changed + " rattachements modifies).");

        // Les apparitions se jugent par serie : les saisons d'une franchise n'en sont pas.
        int guests = persistenceService.applyGuestAppearances(type);
        status.log(type + " : " + guests + " apparitions de personnages hors de leur oeuvre d'origine.");
    }

    /**
     * Etape 2 : couvertures de volumes MangaDex.
     *
     * AniList ne donne qu'une image par manga, insuffisant pour le mode Images qui en demande 3.
     */
    private void ingestMangaCovers() {
        status.log("Correspondance AniList <-> MangaDex...");

        Map<Integer, String> mapping;
        try {
            mapping = mangaDexClient.fetchAniListIdMapping(mangadexMappingSize);
        } catch (CatalogHttpException e) {
            status.log("ATTENTION balayage MangaDex indisponible, recherche par titre seule : " + e.getMessage());
            mapping = new HashMap<>();
        }

        List<MediaWork> unmapped = workRepository.findMangaWithoutMangadexId().stream()
                .sorted(Comparator.comparing(
                        (MediaWork work) -> Optional.ofNullable(work.getPopularity()).orElse(0)).reversed())
                .toList();
        searchMangaDexByTitle(unmapped, mapping);

        Map<Integer, String> found = mapping;
        List<MediaWork> mangas = unmapped.stream()
                .filter(work -> found.containsKey(work.getAnilistId()))
                .limit(maxMangaForCovers)
                .toList();

        status.log(mangas.size() + " mangas a enrichir en couvertures de volumes.");
        int done = 0;

        for (MediaWork manga : mangas) {
            String mangadexId = found.get(manga.getAnilistId());
            try {
                List<MangaDexClient.VolumeCover> covers =
                        mangaDexClient.fetchVolumeCovers(mangadexId, VOLUME_COVERS_PER_MANGA);
                persistenceService.attachVolumeCovers(manga.getId(), mangadexId, covers);
            } catch (CatalogHttpException e) {
                status.log("ATTENTION couvertures indisponibles pour " + manga.displayTitle() + " : " + e.getMessage());
            }

            done++;
            status.progress("couvertures manga", done, mangas.size());
            if (done % 50 == 0) {
                status.log("Couvertures : " + done + "/" + mangas.size());
            }
        }

        status.log("Couvertures de volumes : " + done + " mangas traites.");
    }

    /**
     * Complement du balayage : recherche par titre des mangas qu'il n'a pas atteints.
     *
     * Un manga absent de MangaDex est recherche a chaque ingestion (~2 requetes, 4 par seconde) :
     * compter quelques minutes pour un millier. Une serie d'echecs consecutifs signale un blocage
     * de leur cote plutot qu'un manga introuvable : on s'arrete alors en gardant l'acquis.
     */
    private void searchMangaDexByTitle(List<MediaWork> unmapped, Map<Integer, String> mapping) {
        List<MediaWork> targets = unmapped.stream()
                .filter(work -> work.getAnilistId() != null && !mapping.containsKey(work.getAnilistId()))
                .limit(maxMangaForTitleSearch)
                .toList();
        status.log("Recherche MangaDex par titre : " + targets.size() + " mangas non trouves par le balayage.");

        int done = 0;
        int matched = 0;
        int consecutiveFailures = 0;
        for (MediaWork manga : targets) {
            try {
                Optional<String> mangadexId = mangaDexClient.findByAniListId(
                        manga.getAnilistId(), Arrays.asList(manga.getTitleRomaji(), manga.getTitleEnglish()));
                if (mangadexId.isPresent()) {
                    mapping.put(manga.getAnilistId(), mangadexId.get());
                    matched++;
                }
                consecutiveFailures = 0;
            } catch (CatalogHttpException e) {
                if (++consecutiveFailures >= MAX_CONSECUTIVE_SEARCH_FAILURES) {
                    status.log("ATTENTION recherche par titre interrompue apres " + consecutiveFailures
                            + " echecs consecutifs : " + e.getMessage());
                    break;
                }
            }

            done++;
            status.progress("recherche MangaDex", done, targets.size());
            if (done % 100 == 0) {
                status.log("Recherche par titre : " + done + "/" + targets.size() + " (" + matched + " trouves)");
            }
        }
        status.log("Recherche par titre : " + matched + " mangas rattaches sur " + done + " cherches.");
    }

    /**
     * Etape 2 bis : pages de chapitres MangaDex, qui alimentent le mode Images manga.
     *
     * Seuls les mangas sans pages sont traites : les URLs stockees sont stables, et chaque
     * manga coute quatre requetes dont trois sur l'endpoint at-home, plafonne a 40 par minute.
     */
    private void ingestMangaChapterPages() {
        List<MediaWork> mangas = workRepository.findMangaNeedingChapterPages().stream()
                .limit(maxMangaForPages)
                .toList();

        status.log(mangas.size() + " mangas a enrichir en pages de chapitres.");
        int done = 0;
        int withPages = 0;

        for (MediaWork manga : mangas) {
            try {
                List<MangaDexClient.ChapterPage> pages =
                        mangaDexClient.fetchChapterPages(manga.getMangadexId(), chaptersPerManga, pagesPerChapter);
                if (!pages.isEmpty()) {
                    persistenceService.attachChapterPages(manga.getId(), pages);
                    withPages++;
                }
            } catch (CatalogHttpException e) {
                status.log("ATTENTION pages indisponibles pour " + manga.displayTitle() + " : " + e.getMessage());
            }

            done++;
            status.progress("pages manga", done, mangas.size());
            if (done % 25 == 0) {
                status.log("Pages de chapitres : " + done + "/" + mangas.size() + " (" + withPages + " avec pages)");
            }
        }

        status.log("Pages de chapitres : " + withPages + " mangas sur " + done + " en possedent.");
    }

    /** Etape 3 : openings et endings, seule source possible pour les modes Opening / Ending. */
    private void ingestAnimeThemes() {
        status.log("Recuperation des generiques (AnimeThemes)...");

        List<MediaWork> animes = workRepository
                .findByTypeOrderByPopularityDesc(WorkType.ANIME,
                        org.springframework.data.domain.PageRequest.of(0, maxWorksForThemes))
                .getContent();

        int done = 0;
        int withThemes = 0;

        for (MediaWork anime : animes) {
            try {
                List<MediaTheme> themes = animeThemesClient.fetchThemes(anime.getAnilistId());
                if (!themes.isEmpty()) {
                    persistenceService.attachThemes(anime.getId(), themes);
                    withThemes++;
                }
            } catch (CatalogHttpException e) {
                status.log("ATTENTION generiques indisponibles pour " + anime.displayTitle() + " : " + e.getMessage());
            }

            done++;
            status.progress("generiques", done, animes.size());
            if (done % 50 == 0) {
                status.log("Generiques : " + done + "/" + animes.size() + " (" + withThemes + " trouves)");
            }
        }

        status.log("Generiques : " + withThemes + " animes sur " + done + " en possedent.");
    }

    /**
     * Etape 4 : paliers de difficulte, par rang de <b>notoriete</b> dans leur univers.
     *
     * Le classement n'utilise pas la popularite brute mais {@link RecognitionScore}, qui tient
     * compte des favoris et de l'adaptation : la popularite seule envoyait des webtoons de 2022
     * en « facile » et Evangelion en « moyen ». Les rangs de coupure sont propres a chaque
     * univers ({@link CatalogTiers}) : un rang manga et un rang anime ne valent pas la meme
     * chose. Au-dela du dernier rang, une oeuvre perd son palier et n'est plus tiree.
     */
    @Transactional
    public void assignDifficultyTiers() {
        status.log("Calcul des paliers de difficulte...");

        for (WorkType type : WorkType.values()) {
            List<WorkAudience> works = workRepository.findAudienceByType(type);
            if (works.isEmpty()) {
                continue;
            }

            List<Long> orderedIds = works.stream()
                    .sorted(Comparator.comparingDouble(
                            (WorkAudience w) -> RecognitionScore.audience(
                                    w.popularity(), w.favourites(), w.adaptationPopularity())).reversed())
                    .map(WorkAudience::id)
                    .toList();

            CatalogTiers.Ranks ranks = tiers.forType(type);
            DifficultyTiers split = DifficultyTiers.byRank(orderedIds,
                    ranks.getEasyMaxRank(), ranks.getMediumMaxRank(), ranks.getHardMaxRank());
            persistenceService.applyDifficultyTier(Difficulty.EASY, split.easy());
            persistenceService.applyDifficultyTier(Difficulty.MEDIUM, split.medium());
            persistenceService.applyDifficultyTier(Difficulty.HARD, split.hard());
            persistenceService.applyDifficultyTier(null, split.unranked());

            status.log(type + " : " + split.easy().size() + " faciles, " + split.medium().size()
                    + " moyens, " + split.hard().size() + " difficiles, "
                    + split.unranked().size() + " hors paliers.");
        }
    }

    /** Nature d'image attendue par le mode Images, selon l'univers. */
    public static ImageKind primaryImageKind(WorkType type) {
        return type == WorkType.ANIME ? ImageKind.EPISODE_THUMB : ImageKind.CHAPTER_PAGE;
    }

    /** Utilitaire de test : nombre d'images d'une nature donnee. */
    public static long countImages(MediaWork work, ImageKind kind) {
        return work.getImages().stream().filter(image -> image.getKind() == kind).count();
    }

    /** Utilitaire de test : images d'une nature donnee, ordonnees. */
    public static List<MediaImage> imagesOfKind(MediaWork work, ImageKind kind) {
        return work.getImages().stream()
                .filter(image -> image.getKind() == kind)
                .sorted(Comparator.comparingInt(MediaImage::getOrdinal))
                .toList();
    }
}
