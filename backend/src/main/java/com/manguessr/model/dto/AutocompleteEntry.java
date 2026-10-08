package com.manguessr.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Une suggestion de l'index d'autocompletion.
 *
 * Une <b>serie</b> entiere n'occupe qu'une entree : ses saisons, parties et suites valent la
 * meme reponse, et les lister separement remplissait les cinq suggestions affichees avec des
 * variantes d'une seule oeuvre. Les autres titres de la serie deviennent des {@code aliases} :
 * ils ne s'affichent pas, mais la saisie les trouve, sinon « Boruto » ou « Brotherhood »
 * seraient introuvables au clavier.
 *
 * @param label   titre montre au joueur
 * @param aliases autres titres qui menent a cette suggestion ; absent s'il n'y en a pas
 */
public record AutocompleteEntry(String label, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> aliases) {

    public static AutocompleteEntry of(String label) {
        return new AutocompleteEntry(label, List.of());
    }
}
