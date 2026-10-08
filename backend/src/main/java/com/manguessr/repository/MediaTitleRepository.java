package com.manguessr.repository;

import com.manguessr.model.entity.MediaTitle;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.projection.TitleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface MediaTitleRepository extends JpaRepository<MediaTitle, Long> {

    /**
     * Index d'autocompletion : chaque titre avec sa nature et son oeuvre, pour un univers donne.
     * La nature et l'oeuvre permettent d'ecarter les synonymes etrangers (un synonyme se juge
     * face aux titres officiels de sa propre oeuvre) sans toucher aux titres officiels.
     */
    // distinct : deux oeuvres distinctes partagent parfois un libelle (une saison et sa reedition),
    // et le front ne doit pas proposer deux fois la meme suggestion.
    @Query("select distinct new com.manguessr.repository.projection.TitleEntry("
            + "t.work.id, t.work.anilistId, t.work.seriesId, t.value, t.kind) "
            + "from MediaTitle t where t.work.type = :type and t.work.nsfw = false order by t.value")
    List<TitleEntry> findAllEntriesByType(@Param("type") WorkType type);

    /**
     * Formes normalisees acceptees pour une serie entiere.
     *
     * Le joueur qui reconnait « Attack on Titan » ne doit pas perdre parce que le tirage est
     * tombe sur la saison 2 : tous les titres de la franchise valent reponse.
     */
    @Query("select distinct t.normalized from MediaTitle t where t.work.seriesId = :seriesId")
    Set<String> findNormalizedBySeriesId(@Param("seriesId") Integer seriesId);

    /** Resolution d'une reponse de joueur : toutes les entrees partageant la forme normalisee. */
    @Query("select t from MediaTitle t where t.normalized = :normalized and t.work.type = :type")
    List<MediaTitle> findByNormalizedAndType(@Param("normalized") String normalized,
                                             @Param("type") WorkType type);
}
