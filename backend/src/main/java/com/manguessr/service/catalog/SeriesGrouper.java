package com.manguessr.service.catalog;

import com.manguessr.service.catalog.dto.WorkRelation;
import com.manguessr.util.TextNormalizer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regroupe les oeuvres d'un univers en series, par fermeture transitive de leurs relations.
 *
 * Une serie est ce qu'un joueur designe d'un seul titre : « Shingeki no Kyojin » couvre ses
 * cinq saisons et ses OAV, « JoJo no Kimyou na Bouken » ses huit parties. Sans ce
 * regroupement, une bonne reponse est comptee fausse des que le tirage tombe sur une suite.
 *
 * Le representant d'un groupe est son oeuvre <b>la plus populaire</b> : c'est celle que le
 * joueur nomme spontanement, et elle est stable d'une ingestion a l'autre.
 *
 * Ce qui empeche les groupes de degenerer (tout Gundam dans un seul sac) : seules les
 * relations de {@link #MERGED_RELATION_TYPES} sont suivies — ni adaptation, ni « personnage
 * commun », ni « autre ».
 *
 * Une oeuvre <b>hors catalogue</b> peut servir de pont : AniList ne relie parfois deux saisons
 * que par un court-metrage intermediaire (« Shingeki no Bahamut: GENESIS » et « VIRGIN SOUL »
 * passent par un « GENESIS - Short story » absent du catalogue). Mais un pont peut aussi etre
 * un <b>crossover</b> : « FAIRY TAIL &amp; Nanatsu no Taizai Gassaku Manga », spin-off des deux
 * series, fusionnait Seven Deadly Sins dans Fairy Tail — « Fairy Tail » devenait une bonne
 * reponse, et le manga disparaissait de l'autocompletion derriere ce libelle. Un pont ne relie
 * donc deux oeuvres que si elles <b>commencent par le meme titre</b> ou si l'une des deux le
 * cite comme <b>suite ou prequelle</b> (voir {@link #bridgeHolds}). Mesure du 21/09/2026 : cette
 * condition ne coupe que quatre fusions, toutes des crossovers entre franchises distinctes
 * (Fairy Tail / Seven Deadly Sins, xxxHolic / Tsubasa, Tsukihime / Fate, deux webtoons), et
 * aucune cote anime. Chacune des deux conditions seule cassait de vraies series : le titre seul
 * separait Gintama de son film « Gekijouban Gintama », la continuite seule separait Re:Zero,
 * Overlord et Pokemon de leurs side-stories.
 *
 * Mesure du 18/09/2026 sur les 2001 oeuvres de chaque univers : 1270 series cote anime
 * (un tiers des fiches sont donc des saisons ou des suites) et 1877 cote manga ; plus gros
 * groupe 23 (Fate) et 13 (JoJo), aucun agregat parasite. Sans les ponts : 1292 et 1886 series,
 * pour un plus gros groupe de 19 et 13 — le gain de 31 franchises recollees ne coute donc
 * presque rien en largeur de groupe.
 */
public final class SeriesGrouper {

    /**
     * Relations qui font une meme serie.
     *
     * {@code ADAPTATION} et {@code SOURCE} sont exclues — elles relient un manga a son anime,
     * qui ne sont jamais compares — de meme que {@code CHARACTER} et {@code OTHER}, qui
     * relient des oeuvres seulement voisines (un cameo, un meme auteur).
     */
    public static final List<String> MERGED_RELATION_TYPES = List.of(
            "SEQUEL", "PREQUEL", "PARENT", "SIDE_STORY", "ALTERNATIVE",
            "SPIN_OFF", "SUMMARY", "COMPILATION", "CONTAINS");

    private SeriesGrouper() {}

    /**
     * @param anilistIdsByPopularityDesc oeuvres de l'univers, la plus populaire d'abord
     * @param relations                  liens entre oeuvres, y compris vers des oeuvres non ingerees
     * @param titles                     titre romaji de chaque oeuvre du catalogue, pour juger les ponts
     * @return pour chaque oeuvre <b>du catalogue</b>, l'identifiant AniList du representant de
     *         sa serie ; une oeuvre hors catalogue n'apparait pas, meme si elle a servi de pont
     */
    public static Map<Integer, Integer> group(List<Integer> anilistIdsByPopularityDesc,
                                              Collection<WorkRelation> relations,
                                              Map<Integer, String> titles) {
        Map<Integer, Integer> parent = new HashMap<>();
        for (Integer id : anilistIdsByPopularityDesc) {
            parent.put(id, id);
        }

        // Oeuvre hors catalogue -> oeuvres du catalogue qui la citent, avec la nature du lien.
        Map<Integer, Map<Integer, WorkRelation>> citingByBridge = new HashMap<>();
        for (WorkRelation relation : relations) {
            if (!parent.containsKey(relation.fromAnilistId())) {
                continue;
            }
            if (parent.containsKey(relation.toAnilistId())) {
                union(parent, relation.fromAnilistId(), relation.toAnilistId());
            } else {
                citingByBridge.computeIfAbsent(relation.toAnilistId(), key -> new LinkedHashMap<>())
                        .merge(relation.fromAnilistId(), relation,
                                (kept, other) -> kept.isContinuity() ? kept : other);
            }
        }

        for (Map<Integer, WorkRelation> citing : citingByBridge.values()) {
            List<WorkRelation> links = List.copyOf(citing.values());
            for (int i = 0; i < links.size(); i++) {
                for (int j = i + 1; j < links.size(); j++) {
                    if (bridgeHolds(links.get(i), links.get(j), titles)) {
                        union(parent, links.get(i).fromAnilistId(), links.get(j).fromAnilistId());
                    }
                }
            }
        }

        // Les oeuvres arrivent triees : le premier representant rencontre est le plus populaire.
        Map<Integer, Integer> leaderByRoot = new HashMap<>();
        Map<Integer, Integer> seriesByWork = new LinkedHashMap<>();
        for (Integer id : anilistIdsByPopularityDesc) {
            seriesByWork.put(id, leaderByRoot.computeIfAbsent(find(parent, id), root -> id));
        }
        return seriesByWork;
    }

    /**
     * Deux oeuvres qui citent la meme oeuvre hors catalogue sont-elles de la meme serie ?
     *
     * Oui si l'une la cite comme suite ou prequelle — le meme recit se poursuit — ou si leurs
     * titres commencent pareil. Non sinon : c'est le profil d'un crossover, spin-off commun
     * de deux franchises distinctes.
     */
    static boolean bridgeHolds(WorkRelation first, WorkRelation second, Map<Integer, String> titles) {
        return first.isContinuity() || second.isContinuity()
                || sameTitleRoot(titles.get(first.fromAnilistId()), titles.get(second.fromAnilistId()));
    }

    /**
     * Les deux titres commencent-ils par les memes mots ? Deux mots, ou le titre entier s'il
     * n'en compte qu'un : « Overlord » et « Overlord: Fushisha no Oh » se ressemblent,
     * « FAIRY TAIL » et « Nanatsu no Taizai » non.
     */
    static boolean sameTitleRoot(String first, String second) {
        String[] a = TextNormalizer.normalize(first).split(" ");
        String[] b = TextNormalizer.normalize(second).split(" ");
        int words = Math.min(2, Math.min(a.length, b.length));
        if (words == 0 || a[0].isEmpty() || b[0].isEmpty()) {
            return false;
        }
        for (int i = 0; i < words; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static void union(Map<Integer, Integer> parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent.put(rootA, rootB);
        }
    }

    private static int find(Map<Integer, Integer> parent, int node) {
        int root = node;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        // Compression de chemin : la fermeture transitive reste lineaire sur 2000 oeuvres.
        int cursor = node;
        while (parent.get(cursor) != root) {
            int next = parent.get(cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }
}
