package com.manguessr.service.catalog;

/**
 * Score de notoriete d'une oeuvre : l'audience qui la reconnaitrait.
 *
 * Remplace la popularite brute d'AniList pour classer les oeuvres par difficulte. La
 * popularite seule compte les gens qui <b>suivent</b> l'oeuvre sur AniList, ce qui favorise
 * lourdement le recent et le webtoon en cours de parution : au rang 150 « facile » on trouvait
 * des webtoons coreens de 2022, pendant qu'Evangelion (rang 179), Parasyte (227), Erased (257)
 * ou Re:Zero (252) etaient classes moyens — des oeuvres que tout le monde reconnait, mais que
 * le public a vues en anime plutot que lues.
 *
 * Trois termes, cales le 19/09/2026 contre une liste de 27 oeuvres de reference etablie avant
 * la mesure, moitie « connues par leur anime », moitie « connues comme manga » :
 * <ul>
 *   <li>la <b>popularite</b>, l'audience de base ;</li>
 *   <li>les <b>favoris</b>, ponderes {@value #FAVOURITE_WEIGHT} : mettre une oeuvre en favori
 *       est un signal d'attachement bien plus fort que la suivre, et il rattrape les classiques
 *       que peu de gens « suivent » encore ;</li>
 *   <li>l'audience de l'<b>adaptation</b>, ponderee {@value #ADAPTATION_WEIGHT} : voir l'anime
 *       apprend les personnages et l'univers, pas le trait du dessinateur — d'ou un poids
 *       faible, et non une simple addition.</li>
 * </ul>
 * Scores obtenus sur la liste de reference (oeuvres classees « faciles », sur 27) :
 * 18 avec la popularite seule, 23 en ajoutant l'adaptation, <b>26</b> avec les trois termes.
 * Cote anime, la meme ponderation des favoris fait passer 21 references sur 30 a 24.
 */
public final class RecognitionScore {

    /** Un favori vaut dix suiveurs : valeur retenue apres balayage (2, 5, 10, 20, 40). */
    public static final int FAVOURITE_WEIGHT = 10;

    /**
     * Dix pour cent de l'audience de l'adaptation. Au-dela, le classement bascule : les oeuvres
     * sans adaptation degringolent en bloc, et Vagabond, Punpun ou 20th Century Boys — qui n'ont
     * pas d'anime — sortaient du palier facile. Balayage : 0 / 0,1 / 0,15 / 0,2 / 0,3 / 0,5.
     */
    public static final double ADAPTATION_WEIGHT = 0.1;

    private RecognitionScore() {}

    /**
     * @param popularity           nombre de listes AniList contenant l'oeuvre
     * @param favourites           nombre de mises en favori
     * @param adaptationPopularity popularite de l'anime tire du manga (ou l'inverse), 0 sans
     * @return une audience comparable <b>au sein d'un univers</b> : les deux catalogues n'ont
     *         pas la meme echelle, un rang manga et un rang anime ne se comparent pas
     */
    public static double audience(Integer popularity, Integer favourites, Integer adaptationPopularity) {
        return value(popularity)
                + FAVOURITE_WEIGHT * value(favourites)
                + ADAPTATION_WEIGHT * value(adaptationPopularity);
    }

    private static int value(Integer count) {
        return count == null || count < 0 ? 0 : count;
    }
}
