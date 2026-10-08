package com.manguessr.model.entity;

import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Puzzle quotidien d'un mode et d'un univers, fige en base.
 *
 * Le figer plutot que le recalculer a la volee garantit deux choses : tous les joueurs du
 * jour affrontent exactement les memes manches, et une re-ingestion du catalogue en cours de
 * journee ne change pas le puzzle de ceux qui ne l'ont pas encore commence.
 */
@Entity
@Table(name = "daily_puzzle", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_puzzle", columnNames = {"puzzle_date", "mode", "theme"}))
public class DailyPuzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Jour du puzzle, en UTC. */
    @Column(name = "puzzle_date", nullable = false)
    private LocalDate puzzleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private WorkType theme;

    @OneToMany(mappedBy = "puzzle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordinal ASC")
    private List<PuzzleRound> rounds = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public DailyPuzzle() {}

    public DailyPuzzle(LocalDate puzzleDate, GameMode mode, WorkType theme) {
        this.puzzleDate = puzzleDate;
        this.mode = mode;
        this.theme = theme;
    }

    public void addRound(PuzzleRound round) {
        round.setPuzzle(this);
        rounds.add(round);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getPuzzleDate() { return puzzleDate; }
    public void setPuzzleDate(LocalDate puzzleDate) { this.puzzleDate = puzzleDate; }

    public GameMode getMode() { return mode; }
    public void setMode(GameMode mode) { this.mode = mode; }

    public WorkType getTheme() { return theme; }
    public void setTheme(WorkType theme) { this.theme = theme; }

    public List<PuzzleRound> getRounds() { return rounds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
