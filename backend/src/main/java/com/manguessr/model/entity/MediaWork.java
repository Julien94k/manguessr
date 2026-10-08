package com.manguessr.model.entity;

import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.WorkType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.List;
import java.util.Set;

/**
 * Une oeuvre du catalogue : un anime ou un manga, ingere depuis AniList.
 *
 * C'est la seule source de verite pendant une partie — aucune API tierce n'est appelee
 * en jeu. Les collections filles sont en cascade ALL + orphanRemoval pour qu'un
 * reingest remplace proprement les donnees derivees.
 */
@Entity
@Table(name = "media_work", indexes = {
        @Index(name = "idx_work_type_popularity", columnList = "type, popularity"),
        @Index(name = "idx_work_type_tier", columnList = "type, difficulty_tier"),
        @Index(name = "idx_work_series", columnList = "series_id")
})
public class MediaWork {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant AniList, unique tous types confondus (une seule table Media chez eux). */
    @Column(nullable = false, unique = true)
    private Integer anilistId;

    private Integer malId;

    /**
     * Groupe de serie : l'identifiant AniList de l'oeuvre la plus populaire de la franchise.
     *
     * Les saisons, parties et suites sont des oeuvres distinctes chez AniList (« Shingeki no
     * Kyojin Season 2 », « JoJo no Kimyou na Bouken: Stone Ocean »). Repondre le titre de la
     * serie serait compte faux sans ce regroupement. Calcule a l'ingestion depuis les relations
     * AniList (voir {@code SeriesGrouper}) ; une oeuvre sans suite est seule dans son groupe et
     * porte son propre identifiant.
     */
    @Column(name = "series_id")
    private Integer seriesId;

    /** UUID MangaDex, renseigne apres l'etape de mapping. Necessaire aux couvertures de volumes. */
    @Column(length = 36)
    private String mangadexId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private WorkType type;

    private String titleRomaji;
    private String titleEnglish;
    private String titleNative;

    /** Nom de colonne explicite : "year" est un mot reserve SQL. */
    @Column(name = "release_year")
    private Integer year;

    @Column(length = 32)
    private String format;

    @Column(length = 32)
    private String status;

    /** Oeuvre d'origine : MANGA, ORIGINAL, LIGHT_NOVEL... Colonne "Source" du mode Wordle. */
    @Column(length = 32)
    private String source;

    @Column(length = 4)
    private String countryOfOrigin;

    /** Note AniList sur 100. Colonne "Score" du mode Wordle. */
    private Integer averageScore;

    /** Sert au classement par popularite et au calcul du palier de difficulte. */
    private Integer popularity;

    private Integer favourites;

    /**
     * Popularite de l'anime tire de ce manga (ou du manga dont cet anime est tire), la plus
     * forte s'il y en a plusieurs. Sert au score de notoriete : une oeuvre connue par son
     * adaptation se devine, meme si peu de gens suivent la version d'origine.
     */
    @Column(name = "adaptation_popularity")
    private Integer adaptationPopularity;

    private Integer episodes;
    private Integer chapters;
    private Integer volumes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 512)
    private String coverUrl;

    @Column(length = 512)
    private String bannerUrl;

    @Column(length = 16)
    private String coverColor;

    @Column(nullable = false)
    private boolean nsfw = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_tier", length = 8)
    private Difficulty difficultyTier;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "media_work_genre", joinColumns = @JoinColumn(name = "work_id"))
    @Column(name = "genre", length = 64)
    private Set<String> genres = new LinkedHashSet<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaTitle> titles = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaTag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaCredit> credits = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaCharacter> characters = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MediaTheme> themes = new ArrayList<>();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public MediaWork() {}

    /**
     * Une oeuvre isolee est sa propre serie.
     *
     * Pose ici plutot que par une valeur par defaut : le groupe n'est connu qu'apres coup, et
     * la colonne doit rester interrogeable sans {@code coalesce} pour utiliser son index.
     */
    @PrePersist
    void defaultSeriesToSelf() {
        if (seriesId == null) {
            seriesId = anilistId;
        }
    }

    /** Groupe de serie, avec repli sur l'oeuvre elle-meme tant que l'ingestion ne l'a pas calcule. */
    public Integer seriesKey() {
        return seriesId != null ? seriesId : anilistId;
    }

    /**
     * Les deux oeuvres appartiennent-elles a la meme serie ?
     *
     * C'est la question que posent le jugement des reponses, le tirage des manches et la
     * detection des doublons : « Attack on Titan » et sa saison 2 ne font qu'une.
     */
    public boolean sameSeriesAs(MediaWork other) {
        // Une cle inconnue ne vaut jamais egalite : deux oeuvres non identifiees seraient
        // sinon confondues en une seule serie, ce qui offrirait des reponses.
        return other != null && seriesKey() != null && seriesKey().equals(other.seriesKey());
    }

    /** Titre d'affichage : le romaji d'AniList, avec repli sur l'anglais puis le natif. */
    /**
     * Titre montre au joueur : indice des initiales, solution, tableaux de resultats.
     *
     * L'anglais d'abord (choix produit) ; le romaji a defaut, beaucoup de mangas n'ayant pas de
     * titre anglais officiel. La validation des reponses, elle, accepte tous les titres connus.
     */
    public String displayTitle() {
        if (titleEnglish != null && !titleEnglish.isBlank()) return titleEnglish;
        if (titleRomaji != null && !titleRomaji.isBlank()) return titleRomaji;
        return titleNative;
    }

    public void addTitle(MediaTitle title) {
        title.setWork(this);
        titles.add(title);
    }

    public void addTag(MediaTag tag) {
        tag.setWork(this);
        tags.add(tag);
    }

    public void addCredit(MediaCredit credit) {
        credit.setWork(this);
        credits.add(credit);
    }

    public void addCharacter(MediaCharacter character) {
        character.setWork(this);
        characters.add(character);
    }

    public void addImage(MediaImage image) {
        image.setWork(this);
        images.add(image);
    }

    public void addTheme(MediaTheme theme) {
        theme.setWork(this);
        themes.add(theme);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getAnilistId() { return anilistId; }
    public void setAnilistId(Integer anilistId) { this.anilistId = anilistId; }

    public Integer getMalId() { return malId; }
    public void setMalId(Integer malId) { this.malId = malId; }

    public Integer getSeriesId() { return seriesId; }
    public void setSeriesId(Integer seriesId) { this.seriesId = seriesId; }

    public String getMangadexId() { return mangadexId; }
    public void setMangadexId(String mangadexId) { this.mangadexId = mangadexId; }

    public WorkType getType() { return type; }
    public void setType(WorkType type) { this.type = type; }

    public String getTitleRomaji() { return titleRomaji; }
    public void setTitleRomaji(String titleRomaji) { this.titleRomaji = titleRomaji; }

    public String getTitleEnglish() { return titleEnglish; }
    public void setTitleEnglish(String titleEnglish) { this.titleEnglish = titleEnglish; }

    public String getTitleNative() { return titleNative; }
    public void setTitleNative(String titleNative) { this.titleNative = titleNative; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCountryOfOrigin() { return countryOfOrigin; }
    public void setCountryOfOrigin(String countryOfOrigin) { this.countryOfOrigin = countryOfOrigin; }

    public Integer getAverageScore() { return averageScore; }
    public void setAverageScore(Integer averageScore) { this.averageScore = averageScore; }

    public Integer getPopularity() { return popularity; }
    public void setPopularity(Integer popularity) { this.popularity = popularity; }

    public Integer getFavourites() { return favourites; }
    public void setFavourites(Integer favourites) { this.favourites = favourites; }

    public Integer getAdaptationPopularity() { return adaptationPopularity; }
    public void setAdaptationPopularity(Integer adaptationPopularity) { this.adaptationPopularity = adaptationPopularity; }

    public Integer getEpisodes() { return episodes; }
    public void setEpisodes(Integer episodes) { this.episodes = episodes; }

    public Integer getChapters() { return chapters; }
    public void setChapters(Integer chapters) { this.chapters = chapters; }

    public Integer getVolumes() { return volumes; }
    public void setVolumes(Integer volumes) { this.volumes = volumes; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public String getCoverColor() { return coverColor; }
    public void setCoverColor(String coverColor) { this.coverColor = coverColor; }

    public boolean isNsfw() { return nsfw; }
    public void setNsfw(boolean nsfw) { this.nsfw = nsfw; }

    public Difficulty getDifficultyTier() { return difficultyTier; }
    public void setDifficultyTier(Difficulty difficultyTier) { this.difficultyTier = difficultyTier; }

    public Set<String> getGenres() { return genres; }
    public void setGenres(Set<String> genres) { this.genres = genres; }

    public List<MediaTitle> getTitles() { return titles; }
    public List<MediaTag> getTags() { return tags; }
    public List<MediaCredit> getCredits() { return credits; }
    public List<MediaCharacter> getCharacters() { return characters; }
    public List<MediaImage> getImages() { return images; }
    public List<MediaTheme> getThemes() { return themes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
