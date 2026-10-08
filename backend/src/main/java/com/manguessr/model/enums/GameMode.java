package com.manguessr.model.enums;

import java.util.List;

/** Les six modes de jeu, et les univers ou chacun est disponible. */
public enum GameMode {

    /** 3 images de plus en plus parlantes, plus un indice sur les initiales du titre. */
    IMAGES(List.of(WorkType.ANIME, WorkType.MANGA), 3),

    /** 4 portraits par manche : nom du personnage et titre de l'oeuvre. */
    CHARACTERS(List.of(WorkType.ANIME, WorkType.MANGA), 3),

    /** Generique de debut : audio d'abord, clip video en indice. */
    OPENING(List.of(WorkType.ANIME), 3),

    /** Generique de fin, avec une jaquette floutee offerte en prime. */
    ENDING(List.of(WorkType.ANIME), 3),

    /** Anidle / Mangadle : deduction par comparaison, 22 essais, une seule manche. */
    WORDLE(List.of(WorkType.ANIME, WorkType.MANGA), 1),

    /** Jaquette de plus en plus defloutee, 10 essais. */
    COVER_REVEAL(List.of(WorkType.MANGA), 3);

    private final List<WorkType> supportedTypes;
    private final int roundsPerPuzzle;

    GameMode(List<WorkType> supportedTypes, int roundsPerPuzzle) {
        this.supportedTypes = supportedTypes;
        this.roundsPerPuzzle = roundsPerPuzzle;
    }

    public List<WorkType> getSupportedTypes() { return supportedTypes; }

    /** 3 manches Easy/Medium/Hard, sauf le mode Wordle qui n'en a qu'une. */
    public int getRoundsPerPuzzle() { return roundsPerPuzzle; }

    public boolean supports(WorkType type) { return supportedTypes.contains(type); }

    /** Modes disponibles dans un univers, dans l'ordre d'affichage. */
    public static List<GameMode> forType(WorkType type) {
        return List.of(values()).stream().filter(mode -> mode.supports(type)).toList();
    }
}
