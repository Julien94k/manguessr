package com.manguessr.model.entity;

import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.SessionStatus;
import com.manguessr.model.enums.WorkType;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Une partie : un mode, un univers, et ses manches.
 *
 * Le score total est toujours recalcule a partir des manches, jamais transmis par le client.
 *
 * {@code user} est nullable : on peut jouer sans compte, mais la partie n'est alors
 * rattachee a aucun joueur et n'alimente ni le classement ni la serie de victoires.
 */
@Entity
@Table(name = "game_session", indexes = {
        @Index(name = "idx_session_user_date", columnList = "user_id, play_date"),
        @Index(name = "idx_session_mode_date", columnList = "mode, play_date")
})
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private WorkType theme;

    /** Partie libre : ne compte ni pour le score du jour ni pour la serie. */
    @Column(nullable = false)
    private boolean unlimited = false;

    /** Puzzle quotidien dont la partie est tiree ; nul pour une partie libre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puzzle_id")
    private DailyPuzzle puzzle;

    /** Jour de jeu, en UTC, qui rattache la partie a un puzzle quotidien. */
    @Column(name = "play_date", nullable = false)
    private LocalDate playDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(nullable = false)
    private int totalScore = 0;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordinal ASC")
    private List<SessionRound> rounds = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant finishedAt;

    public GameSession() {}

    public void addRound(SessionRound round) {
        round.setSession(this);
        rounds.add(round);
    }

    /** Somme des scores des manches : la seule definition du score d'une partie. */
    public int recomputeTotalScore() {
        totalScore = rounds.stream().mapToInt(SessionRound::getScore).sum();
        return totalScore;
    }

    /** Une partie est terminee quand plus aucune manche n'est en cours. */
    public boolean allRoundsClosed() {
        return rounds.stream().noneMatch(round -> round.getStatus() == SessionStatus.IN_PROGRESS);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public GameMode getMode() { return mode; }
    public void setMode(GameMode mode) { this.mode = mode; }

    public WorkType getTheme() { return theme; }
    public void setTheme(WorkType theme) { this.theme = theme; }

    public boolean isUnlimited() { return unlimited; }
    public void setUnlimited(boolean unlimited) { this.unlimited = unlimited; }

    public DailyPuzzle getPuzzle() { return puzzle; }
    public void setPuzzle(DailyPuzzle puzzle) { this.puzzle = puzzle; }

    public LocalDate getPlayDate() { return playDate; }
    public void setPlayDate(LocalDate playDate) { this.playDate = playDate; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public List<SessionRound> getRounds() { return rounds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
