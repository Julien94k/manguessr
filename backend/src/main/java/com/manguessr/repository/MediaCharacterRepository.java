package com.manguessr.repository;

import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.enums.WorkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MediaCharacterRepository extends JpaRepository<MediaCharacter, Long> {

    @Query("select distinct c.name from MediaCharacter c "
            + "where c.work.type = :type and c.work.nsfw = false order by c.name")
    List<String> findAllNamesByType(@Param("type") WorkType type);

    @Query("select c from MediaCharacter c where c.normalizedName = :normalized")
    List<MediaCharacter> findByNormalizedName(@Param("normalized") String normalized);

    /**
     * Fiches rattachees a plus de {@code minWorks} oeuvres : des artefacts, pas des personnages.
     *
     * AniList attache une fiche « Narrator » unique, avec une image generique, a 146 animes du
     * catalogue. Comme elle compte parmi les plus populaires de chaque oeuvre, elle sortait dans
     * une manche anime sur cinq — indevinable, et deux oeuvres pouvaient l'afficher cote a cote.
     * Un personnage legitime plafonne a 15 oeuvres (Koyomi Araragi et les saisons de Monogatari).
     */
    @Query("select c.anilistId from MediaCharacter c where c.anilistId is not null "
            + "group by c.anilistId having count(distinct c.work.id) > :minWorks")
    List<Integer> findAnilistIdsSharedByManyWorks(@Param("minWorks") long minWorks);

    /**
     * Charge un personnage avec son oeuvre.
     *
     * Depuis que les quatre portraits d'une manche viennent d'oeuvres differentes, l'oeuvre
     * de chaque personnage fait partie de la reponse : elle doit etre chargee avec lui.
     */
    @Query("select c from MediaCharacter c join fetch c.work where c.id = :id")
    java.util.Optional<MediaCharacter> findWithWork(@Param("id") Long id);

    /**
     * Fiches d'un univers portant un id AniList, avec la serie, l'annee et la popularite de
     * leur oeuvre : de quoi designer l'oeuvre d'origine de chaque personne (voir
     * {@code CharacterHomes}). Colonnes : id, anilistId, role, serie, annee, popularite.
     */
    @Query("select c.id, c.anilistId, c.role, coalesce(w.seriesId, w.anilistId), w.year, w.popularity "
            + "from MediaCharacter c join c.work w where w.type = :type and c.anilistId is not null")
    List<Object[]> findAppearancesByType(@Param("type") WorkType type);

    @Modifying
    @Query("update MediaCharacter c set c.guest = false "
            + "where c.work.id in (select w.id from MediaWork w where w.type = :type)")
    int clearGuestsByType(@Param("type") WorkType type);

    @Modifying
    @Query("update MediaCharacter c set c.guest = true where c.id in :ids")
    int markGuests(@Param("ids") java.util.Collection<Long> ids);

    /**
     * Oeuvres d'un univers ou apparait une personne, apparitions comprises : un titre du
     * mode Personnages vaut reponse s'il designe l'une d'elles.
     */
    @Query("select distinct c.work from MediaCharacter c "
            + "where c.anilistId = :anilistId and c.work.type = :type")
    List<com.manguessr.model.entity.MediaWork> findWorksOfPerson(@Param("anilistId") Integer anilistId,
                                                                  @Param("type") WorkType type);
}
