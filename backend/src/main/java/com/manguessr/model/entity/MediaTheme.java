package com.manguessr.model.entity;

import com.manguessr.model.enums.ThemeKind;
import jakarta.persistence.*;

/**
 * Generique d'ouverture ou de fin, provenant d'AnimeThemes.moe.
 *
 * AniList ne fournit aucun media audio : c'est la seule source des modes Opening et Ending.
 */
@Entity
@Table(name = "media_theme", indexes = @Index(name = "idx_theme_work_kind", columnList = "work_id, kind"))
public class MediaTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private MediaWork work;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private ThemeKind kind;

    /** Numero du generique : OP1, ED2... */
    @Column(nullable = false)
    private int sequence;

    @Column(length = 255)
    private String songTitle;

    @Column(length = 255)
    private String artist;

    @Column(length = 512)
    private String audioUrl;

    @Column(length = 512)
    private String videoUrl;

    public MediaTheme() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MediaWork getWork() { return work; }
    public void setWork(MediaWork work) { this.work = work; }

    public ThemeKind getKind() { return kind; }
    public void setKind(ThemeKind kind) { this.kind = kind; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getSongTitle() { return songTitle; }
    public void setSongTitle(String songTitle) { this.songTitle = songTitle; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
}
