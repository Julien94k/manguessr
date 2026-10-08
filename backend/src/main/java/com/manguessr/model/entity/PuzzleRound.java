package com.manguessr.model.entity;

import com.manguessr.model.enums.Difficulty;
import jakarta.persistence.*;

/**
 * Une manche d'un puzzle quotidien : l'oeuvre et les elements tires, communs a tous les joueurs.
 *
 * Chaque partie quotidienne en copie le contenu dans ses propres {@link SessionRound}, qui
 * portent ensuite la progression individuelle.
 */
@Entity
@Table(name = "puzzle_round", indexes = @Index(name = "idx_puzzle_round_puzzle", columnList = "puzzle_id"))
public class PuzzleRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "puzzle_id", nullable = false)
    private DailyPuzzle puzzle;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Difficulty difficulty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    /** Images, personnages ou generique tires, au format de RoundPayload. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    public PuzzleRound() {}

    public PuzzleRound(int ordinal, Difficulty difficulty, MediaWork work, String payload) {
        this.ordinal = ordinal;
        this.difficulty = difficulty;
        this.work = work;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DailyPuzzle getPuzzle() { return puzzle; }
    public void setPuzzle(DailyPuzzle puzzle) { this.puzzle = puzzle; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
