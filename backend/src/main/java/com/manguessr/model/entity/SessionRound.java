package com.manguessr.model.entity;

import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.SessionStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Une manche d'une partie : l'oeuvre a deviner et l'etat de progression du joueur.
 *
 * L'oeuvre est referencee ici mais n'est <b>jamais</b> serialisee vers le client tant que
 * la manche est en cours : c'est la reponse.
 */
@Entity
@Table(name = "session_round", indexes = @Index(name = "idx_round_session", columnList = "session_id"))
public class SessionRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    /** Position de la manche : 0, 1, 2 pour Easy, Medium, Hard. */
    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Difficulty difficulty;

    /** L'oeuvre a deviner. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    /**
     * Elements tires pour cette manche (identifiants d'images, de personnages, de generique),
     * figes a la creation pour que la manche reste identique si le catalogue evolue.
     */
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    /** Indices payants debloques, hors indice offert. */
    @Column(nullable = false)
    private int cluesUnlocked = 0;

    @Column(nullable = false)
    private int attemptsUsed = 0;

    @Column(nullable = false)
    private int score = 0;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<RoundAttempt> attempts = new ArrayList<>();

    public SessionRound() {}

    public void addAttempt(RoundAttempt attempt) {
        attempt.setRound(this);
        attempts.add(attempt);
    }

    public boolean isOpen() {
        return status == SessionStatus.IN_PROGRESS;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GameSession getSession() { return session; }
    public void setSession(GameSession session) { this.session = session; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public int getCluesUnlocked() { return cluesUnlocked; }
    public void setCluesUnlocked(int cluesUnlocked) { this.cluesUnlocked = cluesUnlocked; }

    public int getAttemptsUsed() { return attemptsUsed; }
    public void setAttemptsUsed(int attemptsUsed) { this.attemptsUsed = attemptsUsed; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<RoundAttempt> getAttempts() { return attempts; }
}
