package com.manguessr.repository;

import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.projection.WorkAudience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaWorkRepository extends JpaRepository<MediaWork, Long> {

    Optional<MediaWork> findByAnilistId(Integer anilistId);

    List<MediaWork> findByAnilistIdIn(List<Integer> anilistIds);

    long countByType(WorkType type);

    Page<MediaWork> findByTypeOrderByPopularityDesc(WorkType type, Pageable pageable);

    /**
     * Signaux de notoriete des oeuvres d'un univers, pour l'attribution des paliers.
     *
     * Le classement se fait en Java, par {@code RecognitionScore} : la formule reste a un seul
     * endroit et testable, plutot que recopiee dans un {@code order by}.
     */
    @Query("select new com.manguessr.repository.projection.WorkAudience("
            + "w.id, w.popularity, w.favourites, w.adaptationPopularity) "
            + "from MediaWork w where w.type = :type and w.nsfw = false")
    List<WorkAudience> findAudienceByType(@Param("type") WorkType type);

    /**
     * Oeuvres d'un univers, la plus populaire d'abord, pour le regroupement en series.
     *
     * Les oeuvres NSFW y figurent, contrairement aux pools de tirage : les exclure couperait
     * une franchise en deux et laisserait leurs suites orphelines.
     */
    @Query("select w.anilistId from MediaWork w where w.type = :type "
            + "order by coalesce(w.popularity, 0) desc")
    List<Integer> findAnilistIdsByTypeOrderByPopularityDesc(@Param("type") WorkType type);

    /** Identifiant AniList et titre romaji : sert a juger si une oeuvre hors catalogue fait pont. */
    @Query("select w.anilistId, w.titleRomaji from MediaWork w where w.type = :type")
    List<Object[]> findRomajiTitlesByType(@Param("type") WorkType type);

    /** Toutes les oeuvres des series citees : sert a etendre une exclusion d'oeuvre a sa serie. */
    @Query("select w.id from MediaWork w where w.seriesId in :seriesIds")
    List<Long> findIdsBySeriesIdIn(@Param("seriesIds") Collection<Integer> seriesIds);

    /** Series des oeuvres citees, pour etendre un historique d'oeuvres a leurs franchises. */
    @Query("select distinct w.seriesId from MediaWork w where w.id in :ids and w.seriesId is not null")
    List<Integer> findSeriesIdsByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Oeuvres disposant d'assez d'images d'une nature donnee pour alimenter le mode Images
     * (3 images par manche).
     */
    @Query("select count(w) from MediaWork w where w.type = :type and w.nsfw = false and "
            + "(select count(i) from MediaImage i where i.work = w and i.kind = :kind) >= :minimum")
    long countWithAtLeastImages(@Param("type") WorkType type,
                                @Param("kind") com.manguessr.model.enums.ImageKind kind,
                                @Param("minimum") long minimum);

    /** Oeuvres ayant assez de personnages illustres pour le mode Personnages (4 par manche). */
    @Query("select count(w) from MediaWork w where w.type = :type and w.nsfw = false and "
            + "(select count(c) from MediaCharacter c where c.work = w) >= :minimum")
    long countWithAtLeastCharacters(@Param("type") WorkType type, @Param("minimum") long minimum);

    /** Animes disposant d'au moins un generique : pool des modes Opening / Ending. */
    @Query("select count(w) from MediaWork w where w.type = com.manguessr.model.enums.WorkType.ANIME "
            + "and (select count(t) from MediaTheme t where t.work = w) > 0")
    long countAnimeWithThemes();

    // --- Pools de tirage par mode de jeu -----------------------------------------
    //
    // Une oeuvre n'entre dans le pool d'un mode que si elle possede reellement la donnee
    // necessaire. C'est indispensable et non cosmetique : un tiers des animes n'a pas
    // 3 vignettes d'episodes, et tirer une oeuvre incomplete produirait une manche injouable.

    /** Mode Images : au moins {@code minimum} images de la nature attendue. */
    @Query("select w.id from MediaWork w where w.type = :type and w.nsfw = false "
            + "and w.difficultyTier = :tier and "
            + "(select count(i) from MediaImage i where i.work = w and i.kind = :kind) >= :minimum")
    List<Long> findImageModePool(@Param("type") WorkType type,
                                 @Param("tier") com.manguessr.model.enums.Difficulty tier,
                                 @Param("kind") com.manguessr.model.enums.ImageKind kind,
                                 @Param("minimum") long minimum);

    /**
     * Mode Personnages : au moins {@code minimum} personnages illustres et exploitables.
     *
     * Depuis que les quatre portraits d'une manche viennent d'oeuvres <b>differentes</b>,
     * un seul personnage illustre suffit a rendre une oeuvre eligible — ce qui elargit
     * fortement le pool.
     *
     * {@code generic} liste les fiches AniList rattachees a des dizaines d'oeuvres
     * (« Narrator ») : une oeuvre dont ce serait le seul portrait ne doit pas entrer dans le
     * pool, sinon le tirage echouerait a lui trouver un personnage. La liste n'est jamais vide
     * ({@code not in ()} est invalide), l'appelant y place une valeur sentinelle.
     */
    @Query("select w.id from MediaWork w where w.type = :type and w.nsfw = false "
            + "and w.difficultyTier = :tier and "
            + "(select count(c) from MediaCharacter c where c.work = w and c.imageUrl is not null "
            + " and (c.guest is null or c.guest = false) "
            + " and (c.anilistId is null or c.anilistId not in :generic)) >= :minimum")
    List<Long> findCharacterModePool(@Param("type") WorkType type,
                                     @Param("tier") com.manguessr.model.enums.Difficulty tier,
                                     @Param("minimum") long minimum,
                                     @Param("generic") List<Integer> generic);

    /** Modes Opening / Ending : au moins un generique du type demande. */
    @Query("select w.id from MediaWork w where w.type = com.manguessr.model.enums.WorkType.ANIME "
            + "and w.nsfw = false and w.difficultyTier = :tier and "
            + "(select count(t) from MediaTheme t where t.work = w and t.kind = :kind "
            + " and t.audioUrl is not null) > 0")
    List<Long> findThemeModePool(@Param("tier") com.manguessr.model.enums.Difficulty tier,
                                 @Param("kind") com.manguessr.model.enums.ThemeKind kind);

    /** Mode Cover-deblur : une jaquette suffit. */
    @Query("select w.id from MediaWork w where w.type = :type and w.nsfw = false "
            + "and w.difficultyTier = :tier and w.coverUrl is not null")
    List<Long> findCoverModePool(@Param("type") WorkType type,
                                 @Param("tier") com.manguessr.model.enums.Difficulty tier);

    /**
     * Mode Wordle : l'oeuvre doit porter assez d'attributs comparables pour que la table
     * de deduction ait un sens (annee, score, genres).
     */
    @Query("select w.id from MediaWork w where w.type = :type and w.nsfw = false "
            + "and w.year is not null and w.averageScore is not null "
            + "and (select count(g) from MediaWork w2 join w2.genres g where w2 = w) > 0")
    List<Long> findWordleModePool(@Param("type") WorkType type);

    /** Chargement complet d'une oeuvre pour une manche, sans requetes N+1 a l'affichage. */
    @Query("select w from MediaWork w left join fetch w.titles where w.id = :id")
    Optional<MediaWork> findWithTitles(@Param("id") Long id);

    /** Oeuvres manga sans identifiant MangaDex : cible de l'etape de mapping. */
    @Query("select w from MediaWork w where w.type = com.manguessr.model.enums.WorkType.MANGA "
            + "and w.mangadexId is null")
    List<MediaWork> findMangaWithoutMangadexId();

    /** Mangas rattaches a MangaDex mais encore sans pages de chapitres, les plus populaires d'abord. */
    @Query("select w from MediaWork w where w.type = com.manguessr.model.enums.WorkType.MANGA "
            + "and w.mangadexId is not null and not exists (select i from MediaImage i where i.work = w "
            + "and i.kind = com.manguessr.model.enums.ImageKind.CHAPTER_PAGE) "
            + "order by w.popularity desc")
    List<MediaWork> findMangaNeedingChapterPages();
}
