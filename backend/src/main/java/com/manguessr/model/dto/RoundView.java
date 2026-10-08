package com.manguessr.model.dto;

import java.util.List;

/**
 * Etat d'une manche tel que le client a le droit de le voir.
 *
 * Regle centrale : {@code solution} reste nul tant que {@code status} vaut IN_PROGRESS.
 * Aucun champ de cet objet ne doit permettre de retrouver l'oeuvre avant la fin de la manche
 * — c'est pourquoi les medias sont des jetons signes et non des URLs de CDN.
 *
 * @param ordinal        position de la manche
 * @param difficulty     palier de difficulte
 * @param status         IN_PROGRESS, SOLVED, FAILED ou SKIPPED
 * @param attemptsUsed   essais consommes
 * @param maxAttempts    essais autorises
 * @param score          points acquis sur cette manche
 * @param maxScore       points encore atteignables
 * @param media          medias a afficher, deja filtres selon les indices debloques
 * @param clues          indices, avec leur cout
 * @param characters     portraits du mode Personnages, sinon vide
 * @param wordleRows     historique de comparaison du mode Wordle, sinon vide
 * @param titleHint      initiales masquees du titre, quand l'indice correspondant est debloque
 * @param synopsis       synopsis, quand l'indice correspondant est debloque
 * @param topCharacter   personnage le plus populaire, quand l'indice correspondant est debloque
 * @param solution       l'oeuvre, uniquement une fois la manche terminee
 */
public record RoundView(
        int ordinal,
        String difficulty,
        String status,
        int attemptsUsed,
        int maxAttempts,
        int score,
        int maxScore,
        List<MediaRef> media,
        List<ClueView> clues,
        List<CharacterSlotView> characters,
        List<WordleRowView> wordleRows,
        String titleHint,
        String synopsis,
        String topCharacter,
        SolutionView solution) {

    /**
     * Un media a afficher.
     *
     * @param kind IMAGE, AUDIO ou VIDEO
     * @param url  URL du proxy, portant un jeton signe
     */
    public record MediaRef(String kind, String url) {}

    /**
     * L'oeuvre revelee en fin de manche.
     *
     * @param title       titre principal
     * @param altTitle    titre alternatif, quand il differe
     * @param year        annee de sortie
     * @param coverUrl    jaquette nette
     * @param externalUrl fiche AniList, pour aller voir l'oeuvre
     * @param songTitle   titre du generique, modes Opening et Ending
     * @param artist      interprete du generique
     */
    public record SolutionView(
            String title,
            String altTitle,
            Integer year,
            String coverUrl,
            String externalUrl,
            String songTitle,
            String artist) {}
}
