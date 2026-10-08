package com.manguessr.model.dto;

/**
 * Etat du catalogue, pour verifier d'un coup d'oeil qu'une ingestion a produit
 * de quoi jouer a chaque mode.
 */
public record CatalogStatsResponse(
        long animeCount,
        long mangaCount,
        long worksWithEnoughImages,
        long worksWithEnoughCharacters,
        long animeWithThemes,
        long mangaWithChapterPages) {}
