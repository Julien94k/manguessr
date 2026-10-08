package com.manguessr.model.entity;

import jakarta.persistence.*;

/**
 * Tag AniList avec son rang de pertinence (0-100).
 *
 * Le rang porte directement le code couleur du mode Wordle : AniGuessr affiche en vert
 * les tags les plus representatifs de l'oeuvre et en jaune les tags secondaires.
 */
@Entity
@Table(name = "media_tag", indexes = @Index(name = "idx_tag_work", columnList = "work_id"))
public class MediaTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    @Column(nullable = false, length = 128)
    private String name;

    /** 0-100 : a quel point le tag decrit l'oeuvre, selon les votes AniList. */
    @Column(name = "tag_rank")
    private Integer rank;

    @Column(length = 64)
    private String category;

    /** Tag revelant un element d'intrigue : exclu des indices pour ne pas spoiler. */
    @Column(nullable = false)
    private boolean spoiler = false;

    public MediaTag() {}

    public MediaTag(String name, Integer rank, String category, boolean spoiler) {
        this.name = name;
        this.rank = rank;
        this.category = category;
        this.spoiler = spoiler;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isSpoiler() { return spoiler; }
    public void setSpoiler(boolean spoiler) { this.spoiler = spoiler; }
}
