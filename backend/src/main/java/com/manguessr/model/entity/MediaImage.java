package com.manguessr.model.entity;

import com.manguessr.model.enums.ImageKind;
import jakarta.persistence.*;

/**
 * Image exploitable en jeu.
 *
 * L'URL d'origine ne doit jamais etre exposee au client : les chemins des CDN contiennent
 * souvent le titre de l'oeuvre. Elle transite par le proxy signe (phase 3).
 */
@Entity
@Table(name = "media_image", indexes = @Index(name = "idx_image_work_kind", columnList = "work_id, kind"))
public class MediaImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImageKind kind;

    @Column(nullable = false, length = 512)
    private String url;

    /** Ordre stable, pour que le tirage d'une manche soit reproductible. */
    @Column(nullable = false)
    private int ordinal;

    /** Numero de volume MangaDex, null pour les autres natures d'image. */
    @Column(length = 16)
    private String volume;

    public MediaImage() {}

    public MediaImage(ImageKind kind, String url, int ordinal, String volume) {
        this.kind = kind;
        this.url = url;
        this.ordinal = ordinal;
        this.volume = volume;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public ImageKind getKind() { return kind; }
    public void setKind(ImageKind kind) { this.kind = kind; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public String getVolume() { return volume; }
    public void setVolume(String volume) { this.volume = volume; }
}
