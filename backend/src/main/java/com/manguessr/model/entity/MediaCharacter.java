package com.manguessr.model.entity;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Personnage d'une oeuvre, trie par nombre de favoris a l'ingestion.
 *
 * Sert au mode Personnages (le portrait est l'enonce) et au dernier indice du mode Wordle
 * (le personnage le plus populaire).
 */
@Entity
@Table(name = "media_character", indexes = {
        @Index(name = "idx_character_work", columnList = "work_id"),
        @Index(name = "idx_character_normalized", columnList = "normalizedName")
})
public class MediaCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    private Integer anilistId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String normalizedName;

    /** Surnoms et variantes d'ordre nom/prenom, tous acceptes comme reponse. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "media_character_alt_name", joinColumns = @JoinColumn(name = "character_id"))
    @Column(name = "alt_name", length = 255)
    private Set<String> alternativeNames = new LinkedHashSet<>();

    @Column(length = 512)
    private String imageUrl;

    private Integer favourites;

    /**
     * Role AniList dans cette oeuvre : MAIN, SUPPORTING ou BACKGROUND.
     * Nom de colonne explicite : "role" est un mot reserve SQL.
     */
    @Column(name = "character_role", length = 16)
    private String role;

    /**
     * Apparition dans une oeuvre qui n'est pas celle du personnage (cameo, crossover) :
     * jamais tiree en jeu. Calculee a l'ingestion par {@code CharacterHomes}.
     */
    @Column(name = "guest_appearance")
    private Boolean guest;

    public MediaCharacter() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public Integer getAnilistId() { return anilistId; }
    public void setAnilistId(Integer anilistId) { this.anilistId = anilistId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public Set<String> getAlternativeNames() { return alternativeNames; }
    public void setAlternativeNames(Set<String> alternativeNames) { this.alternativeNames = alternativeNames; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getFavourites() { return favourites; }
    public void setFavourites(Integer favourites) { this.favourites = favourites; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getGuest() { return guest; }
    public void setGuest(Boolean guest) { this.guest = guest; }

    /** Nul avant la premiere ingestion qui le calcule : la fiche est alors tirable. */
    public boolean isGuest() { return Boolean.TRUE.equals(guest); }
}
