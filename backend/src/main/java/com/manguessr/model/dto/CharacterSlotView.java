package com.manguessr.model.dto;

/**
 * Un portrait du mode Personnages.
 *
 * Le nom et l'oeuvre restent nuls tant que la manche est ouverte : ce sont les reponses.
 *
 * @param slot      position du portrait dans la manche
 * @param imageUrl  portrait, servi via le proxy a jeton signe
 * @param name      nom du personnage, revele en fin de manche uniquement
 * @param workTitle titre de l'oeuvre, revele en fin de manche uniquement
 * @param nameFound le joueur a trouve le nom
 * @param titleFound le joueur a trouve le titre
 */
public record CharacterSlotView(
        int slot,
        String imageUrl,
        String name,
        String workTitle,
        boolean nameFound,
        boolean titleFound) {}
