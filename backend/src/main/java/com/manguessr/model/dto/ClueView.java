package com.manguessr.model.dto;

/**
 * Un indice propose au joueur.
 *
 * @param index    position de l'indice dans la manche
 * @param label    libelle affiche sur le bouton
 * @param cost     points retires si l'indice est debloque
 * @param unlocked deja debloque
 * @param free     offert : ne coute rien et est debloque d'office
 */
public record ClueView(int index, String label, int cost, boolean unlocked, boolean free) {}
