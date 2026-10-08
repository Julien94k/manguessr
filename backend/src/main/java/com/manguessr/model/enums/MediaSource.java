package com.manguessr.model.enums;

/**
 * Table d'origine d'un media servi par le proxy.
 *
 * Les portraits de personnages vivent dans {@code media_character} et non dans
 * {@code media_image} : sans ce discriminant, un jeton resoudrait un identifiant de
 * personnage vers l'image portant le meme numero, donc vers une autre oeuvre.
 *
 * Les generiques ({@code media_theme}) ont deux sources, une par piste : le jeton d'un
 * audio offert ne doit pas permettre de reclamer le clip video, qui coute des points.
 */
public enum MediaSource {
    IMAGE,
    CHARACTER,
    THEME_AUDIO,
    THEME_VIDEO;

    /** Les images passent par le rendu JPEG ; les generiques par le relais en flux. */
    public boolean isStream() {
        return this == THEME_AUDIO || this == THEME_VIDEO;
    }
}
