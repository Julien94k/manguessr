package com.manguessr.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Compte joueur.
 *
 * Repris de revision-guide, avec deux differences voulues :
 * - ajout de {@code username}, le pseudo public affiche au leaderboard
 * - suppression de {@code isVerified} : l'inscription ne passe pas par une verification email
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    /** Pseudo public, unique, affiche dans le leaderboard. */
    @Column(unique = true, nullable = false, length = 24)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    /** "ROLE_USER" ou "ROLE_ADMIN". */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
