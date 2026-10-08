package com.manguessr.model.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un essai du joueur sur une manche.
 *
 * Conserver chaque essai permet de recalculer le score cote serveur a tout moment et
 * d'alimenter la table de comparaison du mode Wordle.
 */
@Entity
@Table(name = "round_attempt", indexes = @Index(name = "idx_attempt_round", columnList = "round_id"))
public class RoundAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private SessionRound round;

    /** Numero d'essai, commence a 1. */
    @Column(nullable = false)
    private int attemptNumber;

    /** Reponse brute telle que saisie, conservee pour l'affichage de l'historique. */
    @Column(length = 512)
    private String rawAnswer;

    @Column(nullable = false)
    private boolean correct = false;

    /** Oeuvre proposee, quand la saisie a pu etre resolue : sert au tableau du mode Wordle. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guessed_work_id")
    private MediaWork guessedWork;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public RoundAttempt() {}

    public RoundAttempt(int attemptNumber, String rawAnswer, boolean correct, MediaWork guessedWork) {
        this.attemptNumber = attemptNumber;
        this.rawAnswer = rawAnswer;
        this.correct = correct;
        this.guessedWork = guessedWork;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SessionRound getRound() { return round; }
    public void setRound(SessionRound round) { this.round = round; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public String getRawAnswer() { return rawAnswer; }
    public void setRawAnswer(String rawAnswer) { this.rawAnswer = rawAnswer; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public MediaWork getGuessedWork() { return guessedWork; }
    public void setGuessedWork(MediaWork guessedWork) { this.guessedWork = guessedWork; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
