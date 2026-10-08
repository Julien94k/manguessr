package com.manguessr.model.entity;

import com.manguessr.model.enums.CreditKind;
import jakarta.persistence.*;

/** Studio d'animation (anime) ou auteur (manga) : colonne "Studio"/"Auteur" du mode Wordle. */
@Entity
@Table(name = "media_credit", indexes = @Index(name = "idx_credit_work", columnList = "work_id"))
public class MediaCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CreditKind kind;

    /** Role brut cote AniList ("Story & Art"), conserve pour l'affichage de fin de manche. */
    /** Nom de colonne explicite : "role" est un mot reserve sur plusieurs moteurs. */
    @Column(name = "credit_role", length = 255)
    private String role;

    public MediaCredit() {}

    public MediaCredit(String name, CreditKind kind, String role) {
        this.name = name;
        this.kind = kind;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CreditKind getKind() { return kind; }
    public void setKind(CreditKind kind) { this.kind = kind; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
