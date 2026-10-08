package com.manguessr.model.dto;

import java.util.List;

/**
 * Index d'autocompletion complet.
 *
 * @param type    univers concerne
 * @param entries suggestions proposables, une par serie, triees alphabetiquement
 * @param count   taille de la liste, pour que le front detecte un index vide
 */
public record AutocompleteResponse(String type, List<AutocompleteEntry> entries, int count) {}
