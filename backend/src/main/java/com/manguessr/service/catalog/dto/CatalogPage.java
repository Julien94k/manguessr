package com.manguessr.service.catalog.dto;

import com.manguessr.model.entity.MediaWork;

import java.util.List;

/**
 * Une page de resultats d'un fournisseur de catalogue.
 *
 * @param works       oeuvres detachees, pretes a etre persistees
 * @param relations   liens de serie entre oeuvres du meme univers, a accumuler sur toutes les
 *                    pages : le regroupement ne peut se faire qu'une fois l'univers entier connu
 * @param hasNextPage vrai s'il reste des pages a parcourir
 * @param lastPage    derniere page disponible, pour le journal de progression
 */
public record CatalogPage(List<MediaWork> works, List<WorkRelation> relations,
                          boolean hasNextPage, int lastPage) {}
