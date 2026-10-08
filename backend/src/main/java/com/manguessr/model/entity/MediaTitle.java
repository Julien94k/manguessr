package com.manguessr.model.entity;

import com.manguessr.model.enums.TitleKind;
import jakarta.persistence.*;

/**
 * Un titre accepte pour une oeuvre : romaji, anglais, natif ou synonyme.
 *
 * La forme normalisee est calculee a l'ingestion et indexee, ce qui permet a la fois
 * l'autocompletion et la validation d'une reponse en une seule requete indexee.
 */
@Entity
@Table(name = "media_title", indexes = {
        @Index(name = "idx_title_normalized", columnList = "normalized"),
        @Index(name = "idx_title_work", columnList = "work_id")
})
public class MediaTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    /** Nom de colonne explicite : "value" est un mot reserve SQL. */
    @Column(name = "title_value", nullable = false, length = 512)
    private String value;

    /** Sortie de TextNormalizer.normalize(value). */
    @Column(nullable = false, length = 512)
    private String normalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TitleKind kind;

    public MediaTitle() {}

    public MediaTitle(String value, String normalized, TitleKind kind) {
        this.value = value;
        this.normalized = normalized;
        this.kind = kind;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getNormalized() { return normalized; }
    public void setNormalized(String normalized) { this.normalized = normalized; }

    public TitleKind getKind() { return kind; }
    public void setKind(TitleKind kind) { this.kind = kind; }
}
