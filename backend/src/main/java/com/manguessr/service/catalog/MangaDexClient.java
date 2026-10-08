package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Client MangaDex : la seule source de <b>plusieurs</b> images par manga.
 *
 * AniList ne fournit qu'une jaquette par manga, ce qui ne suffit pas au mode Images
 * (3 images par manche). MangaDex expose les couvertures de chaque volume — 115 pour One Piece —
 * et surtout les pages des chapitres heberges, qui servent au mode Images : contrairement aux
 * couvertures, une page interieure ne porte pas le titre de l'oeuvre.
 *
 * Le mapping vers AniList se fait par le champ {@code links.al} present sur chaque manga.
 * Plutot que de chercher chaque titre un par un, on parcourt le catalogue MangaDex trie par
 * popularite et on rapproche par identifiant : 99 des 100 premiers portent un {@code links.al}.
 */
@Component
public class MangaDexClient {

    private static final Logger log = LoggerFactory.getLogger(MangaDexClient.class);

    private static final String API = "https://api.mangadex.org";
    private static final String UPLOADS = "https://uploads.mangadex.org/covers";
    /**
     * Origine stable des pages. Le serveur MangaDex@Home renvoye par {@code /at-home/server}
     * est temporaire (quelques minutes) : on ne stocke que le chemin sur l'origine, qui sert
     * les memes fichiers durablement.
     */
    private static final String PAGES = "https://uploads.mangadex.org/data-saver";
    /** MangaDex plafonne ses reponses a 100 elements. */
    private static final int PAGE_SIZE = 100;
    /**
     * Le balayage essuie des 400 passagers : une page HTML de leur frontal, sans cause dans la
     * requete, qui passe a l'identique quelques secondes plus tard (vu a l'offset 3300 le
     * 16/09/2026). Le client HTTP ne rejoue que les 429 et 5xx, d'ou ces essais dedies.
     */
    private static final int MAPPING_ATTEMPTS = 3;
    private static final long MAPPING_RETRY_DELAY_MS = 5_000;

    /** Pages de garde exclues en debut et fin de chapitre : titre du chapitre, credits de traduction. */
    static final int SKIPPED_EDGE_PAGES = 2;
    /** En dessous, un chapitre n'a pas assez de pages interieures (souvent un extra ou une annonce). */
    static final int MIN_CHAPTER_PAGES = 8;
    /** Seuls les premiers chapitres sont candidats : les suivants exposent des spoilers. */
    static final int EARLY_CHAPTERS = 30;

    private final JsonHttpClient http;
    /** L'endpoint at-home a son propre plafond, bien plus bas que le reste de l'API (40/min). */
    private final JsonHttpClient atHomeHttp;

    public MangaDexClient(ObjectMapper objectMapper,
                          @Value("${app.catalog.mangadex.user-agent:manguessr/1.0}") String userAgent,
                          @Value("${app.catalog.mangadex.requests-per-second:4}") int requestsPerSecond,
                          @Value("${app.catalog.mangadex.at-home-requests-per-minute:30}") int atHomeRequestsPerMinute) {
        this.http = new JsonHttpClient(objectMapper, "mangadex", userAgent,
                Duration.ofMillis(Math.max(1, 1000 / Math.max(1, requestsPerSecond))));
        this.atHomeHttp = new JsonHttpClient(objectMapper, "mangadex-at-home", userAgent,
                Duration.ofMillis(60_000L / Math.max(1, atHomeRequestsPerMinute)));
    }

    /**
     * Parcourt le catalogue MangaDex par popularite et construit la table
     * identifiant AniList -> identifiant MangaDex.
     *
     * Une page qui echoue malgre les nouveaux essais arrete le balayage <b>sans perdre</b> les
     * correspondances deja etablies : le 16/09/2026, un 400 passager a l'offset 3300 jetait
     * les 3300 precedentes, et l'etape des pages n'a traite que les mangas deja rattaches.
     *
     * @param maxEntries nombre maximum de mangas a parcourir
     */
    public Map<Integer, String> fetchAniListIdMapping(int maxEntries) {
        Map<Integer, String> mapping = new HashMap<>();

        for (int offset = 0; offset < maxEntries; offset += PAGE_SIZE) {
            String url = API + "/manga?limit=" + PAGE_SIZE
                    + "&offset=" + offset
                    + "&order[followedCount]=desc"
                    + "&contentRating[]=safe&contentRating[]=suggestive";

            JsonNode response = fetchMappingPage(url, offset, mapping.size());
            if (response == null) {
                break;
            }

            JsonNode data = response.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode manga : data) {
                anilistIdOf(manga).ifPresent(anilistId -> mapping.putIfAbsent(anilistId, manga.path("id").asText()));
            }

            // Derniere page atteinte.
            if (offset + PAGE_SIZE >= response.path("total").asInt(0)) {
                break;
            }
        }

        log.info("MangaDex : {} correspondances AniList -> MangaDex etablies", mapping.size());
        return mapping;
    }

    /**
     * Retrouve un manga precis par ses titres, pour ceux que le balayage n'a pas atteints.
     *
     * Le balayage suit le classement MangaDex (par suivis), tres different de la popularite
     * AniList, et s'interrompt sur un 400 vers l'offset 3000 : 935 mangas sur 2000 seulement
     * etaient rattaches. La recherche par titre ne sert qu'a trouver des candidats — le
     * rattachement exige que leur {@code links.al} soit exactement l'identifiant AniList, donc
     * un homonyme n'est jamais retenu. Memes classements de contenu que le balayage.
     *
     * @param titles titres a essayer dans l'ordre (romaji puis anglais)
     */
    public Optional<String> findByAniListId(int anilistId, List<String> titles) {
        for (String title : new LinkedHashSet<>(titles)) {
            if (title == null || title.isBlank()) {
                continue;
            }
            String url = API + "/manga?limit=10"
                    + "&title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                    + "&contentRating[]=safe&contentRating[]=suggestive";
            JsonNode response = http.get(url);
            if (response == null) {
                continue;
            }
            Optional<String> match = matchAniListId(response.path("data"), anilistId);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** Identifiant MangaDex du resultat dont {@code links.al} designe exactement cette oeuvre AniList. */
    static Optional<String> matchAniListId(JsonNode results, int anilistId) {
        for (JsonNode manga : results) {
            if (anilistIdOf(manga).filter(id -> id == anilistId).isPresent()) {
                return Optional.of(manga.path("id").asText());
            }
        }
        return Optional.empty();
    }

    /** {@code links.al} d'un manga MangaDex : stocke en chaine, parfois absent ou mal renseigne. */
    static Optional<Integer> anilistIdOf(JsonNode manga) {
        String anilistId = manga.path("attributes").path("links").path("al").asText(null);
        if (anilistId == null || anilistId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(anilistId.trim()));
        } catch (NumberFormatException e) {
            log.debug("MangaDex : links.al non numerique ({}) sur {}", anilistId, manga.path("id").asText());
            return Optional.empty();
        }
    }

    /** Page du balayage, rejouee sur erreur ; {@code null} pour arreter en gardant l'acquis. */
    private JsonNode fetchMappingPage(String url, int offset, int mappedSoFar) {
        for (int attempt = 1; ; attempt++) {
            try {
                return http.get(url);
            } catch (CatalogHttpException e) {
                if (attempt >= MAPPING_ATTEMPTS) {
                    log.warn("MangaDex : balayage arrete a l'offset {} ({}), {} correspondances conservees",
                            offset, e.getMessage(), mappedSoFar);
                    return null;
                }
                log.warn("MangaDex : {} a l'offset {}, essai {}/{}", e.getMessage(), offset, attempt, MAPPING_ATTEMPTS);
            }
            try {
                Thread.sleep(MAPPING_RETRY_DELAY_MS * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * Couvertures de volumes d'un manga, dedupliquees par volume.
     *
     * MangaDex publie une couverture par volume <b>et par langue</b> : une requete brute
     * renvoie « 1, 1, 1, 2, 2, 2 ». On ne garde qu'une image par volume, en privilegiant
     * l'edition japonaise d'origine.
     *
     * Le volume 1 est exclu : c'est l'illustration la plus reconnaissable, souvent
     * identique a la jaquette AniList deja utilisee ailleurs.
     *
     * @return les URLs de couverture, ordonnees par volume croissant
     */
    public List<VolumeCover> fetchVolumeCovers(String mangadexId, int limit) {
        String url = API + "/cover?limit=" + PAGE_SIZE
                + "&manga[]=" + URLEncoder.encode(mangadexId, StandardCharsets.UTF_8)
                + "&order[volume]=asc";

        JsonNode response = http.get(url);
        if (response == null) {
            return List.of();
        }

        // Une seule couverture par volume : la japonaise si elle existe, sinon la premiere vue.
        Map<String, JsonNode> bestByVolume = new LinkedHashMap<>();
        for (JsonNode cover : response.path("data")) {
            JsonNode attributes = cover.path("attributes");
            String volume = attributes.path("volume").asText(null);
            if (volume == null || volume.isBlank() || "1".equals(volume)) {
                continue;
            }

            JsonNode existing = bestByVolume.get(volume);
            if (existing == null || isJapanese(attributes) && !isJapanese(existing.path("attributes"))) {
                bestByVolume.put(volume, cover);
            }
        }

        List<VolumeCover> covers = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : bestByVolume.entrySet()) {
            String fileName = entry.getValue().path("attributes").path("fileName").asText(null);
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            covers.add(new VolumeCover(
                    entry.getKey(),
                    UPLOADS + "/" + mangadexId + "/" + fileName + ".512.jpg"));
            if (covers.size() >= limit) {
                break;
            }
        }

        return covers;
    }

    /**
     * Pages interieures de quelques chapitres, pour le mode Images.
     *
     * Une requete sur le flux des chapitres, puis une par chapitre retenu pour connaitre ses
     * fichiers. La langue de traduction est indifferente : on ne montre que le dessin, et une
     * bulle dans une langue etrangere cache d'autant mieux les noms des personnages.
     *
     * @param chapters        nombre de chapitres a exploiter
     * @param pagesPerChapter pages retenues par chapitre
     * @return les pages, chapitre par chapitre ; vide si le manga n'a aucun chapitre heberge
     */
    public List<ChapterPage> fetchChapterPages(String mangadexId, int chapters, int pagesPerChapter) {
        String url = API + "/manga/" + URLEncoder.encode(mangadexId, StandardCharsets.UTF_8) + "/feed"
                + "?limit=" + PAGE_SIZE
                // Chapitres heberges ailleurs (MangaPlus...) : aucune page chez MangaDex.
                + "&includeExternalUrl=0&includeEmptyPages=0&includeFuturePublishAt=0"
                + "&contentRating[]=safe&contentRating[]=suggestive"
                + "&order[chapter]=asc";

        JsonNode feed = http.get(url);
        if (feed == null) {
            return List.of();
        }

        List<ChapterRef> refs = new ArrayList<>();
        for (JsonNode chapter : feed.path("data")) {
            JsonNode attributes = chapter.path("attributes");
            refs.add(new ChapterRef(
                    chapter.path("id").asText(),
                    attributes.path("chapter").asText(null),
                    attributes.path("pages").asInt(0)));
        }

        List<ChapterPage> pages = new ArrayList<>();
        for (ChapterRef chapter : selectChapters(refs, chapters)) {
            JsonNode atHome = atHomeHttp.get(API + "/at-home/server/" + chapter.id());
            if (atHome == null) {
                continue;
            }
            String hash = atHome.path("chapter").path("hash").asText(null);
            if (hash == null || hash.isBlank()) {
                continue;
            }
            List<String> files = new ArrayList<>();
            for (JsonNode file : atHome.path("chapter").path("dataSaver")) {
                files.add(file.asText());
            }
            for (String file : interiorPages(files, pagesPerChapter)) {
                pages.add(new ChapterPage(chapter.chapter(), PAGES + "/" + hash + "/" + file));
            }
        }
        return pages;
    }

    /**
     * Chapitres candidats : numerotes, assez longs, un seul par numero (un chapitre existe
     * souvent en dix traductions), repartis sur les premiers chapitres de la serie.
     */
    static List<ChapterRef> selectChapters(List<ChapterRef> feed, int count) {
        Map<String, ChapterRef> byNumber = new LinkedHashMap<>();
        for (ChapterRef ref : feed) {
            if (ref.chapter() == null || ref.chapter().isBlank() || ref.pages() < MIN_CHAPTER_PAGES) {
                continue;
            }
            byNumber.putIfAbsent(ref.chapter(), ref);
        }
        List<ChapterRef> distinct = new ArrayList<>(byNumber.values());
        return spread(distinct.subList(0, Math.min(EARLY_CHAPTERS, distinct.size())), count);
    }

    /** Pages du coeur d'un chapitre, pages de garde exclues. */
    static List<String> interiorPages(List<String> files, int count) {
        if (files.size() < MIN_CHAPTER_PAGES) {
            return List.of();
        }
        return spread(files.subList(SKIPPED_EDGE_PAGES, files.size() - SKIPPED_EDGE_PAGES), count);
    }

    /** Elements regulierement espaces : 3 parmi 30 donne les rangs 5, 15 et 25. */
    static <T> List<T> spread(List<T> items, int count) {
        if (items.isEmpty() || count <= 0) {
            return List.of();
        }
        int taken = Math.min(count, items.size());
        List<T> result = new ArrayList<>(taken);
        for (int i = 0; i < taken; i++) {
            result.add(items.get((2 * i + 1) * items.size() / (2 * taken)));
        }
        return result;
    }

    private boolean isJapanese(JsonNode attributes) {
        return "ja".equals(attributes.path("locale").asText(null));
    }

    /** Une couverture de volume : son numero et son URL en 512 px. */
    public record VolumeCover(String volume, String url) {}

    /** Un chapitre du flux MangaDex, tel qu'il sert a la selection. */
    record ChapterRef(String id, String chapter, int pages) {}

    /** Une page de chapitre : le numero du chapitre et l'URL stable de la page. */
    public record ChapterPage(String chapter, String url) {}
}
