package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.manguessr.model.entity.*;
import com.manguessr.model.enums.*;
import com.manguessr.service.catalog.dto.CatalogPage;
import com.manguessr.service.catalog.dto.WorkRelation;
import com.manguessr.util.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fournisseur AniList.
 *
 * Tout est recupere en une seule requete paginee : metadonnees, titres, tags, credits,
 * personnages et vignettes d'episodes. Verifie a perPage=50 (464 Ko, 1,5 s), sous la
 * limite de complexite d'AniList — inutile de faire une requete par oeuvre.
 */
@Component
public class AniListProvider implements MediaCatalogProvider {

    /** Nombre de personnages conserves par oeuvre : 4 suffisent au mode Personnages, on en garde 6 de marge. */
    /**
     * Le tirage du mode Personnages puise dans les 10 premiers d'une oeuvre populaire : a 6,
     * Bleach n'offrait qu'Ichigo et cinq autres visages. Verifie le 17/09/2026 : 25 par oeuvre
     * sur des pages de 50 passent (493 Ko, 1,2 s), sous la limite de complexite d'AniList.
     */
    private static final int CHARACTERS_PER_WORK = 25;

    private static final String PAGE_QUERY = """
            query($page: Int, $perPage: Int, $type: MediaType) {
              Page(page: $page, perPage: $perPage) {
                pageInfo { currentPage lastPage hasNextPage }
                media(type: $type, sort: POPULARITY_DESC, isAdult: false) {
                  id idMal type format status source countryOfOrigin
                  title { romaji english native }
                  synonyms
                  startDate { year }
                  seasonYear episodes chapters volumes
                  genres averageScore popularity favourites isAdult
                  description(asHtml: false)
                  coverImage { extraLarge color }
                  bannerImage
                  studios(isMain: true) { nodes { name } }
                  staff(perPage: 2) { edges { role node { name { full } } } }
                  tags { name rank category isGeneralSpoiler isMediaSpoiler }
                  streamingEpisodes { thumbnail }
                  characters(sort: FAVOURITES_DESC, perPage: %d) {
                    edges { role node { id name { full alternative } image { large } favourites } }
                  }
                  relations { edges { relationType node { id type popularity } } }
                }
              }
            }
            """.formatted(CHARACTERS_PER_WORK);

    private final AniListClient client;

    public AniListProvider(AniListClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "anilist";
    }

    @Override
    public CatalogPage fetchPage(WorkType type, int page, int perPage) {
        JsonNode data = client.query(PAGE_QUERY, Map.of(
                "page", page,
                "perPage", perPage,
                "type", type.name()));

        JsonNode pageNode = data.path("Page");
        JsonNode pageInfo = pageNode.path("pageInfo");

        List<MediaWork> works = new ArrayList<>();
        List<WorkRelation> relations = new ArrayList<>();
        for (JsonNode mediaNode : pageNode.path("media")) {
            works.add(toWork(mediaNode, type));
            collectRelations(mediaNode, type, relations);
        }

        return new CatalogPage(works, relations,
                pageInfo.path("hasNextPage").asBoolean(false),
                pageInfo.path("lastPage").asInt(0));
    }

    /**
     * Popularite de l'adaptation la plus suivie : l'anime tire de ce manga, ou l'inverse.
     *
     * C'est la meme liste de relations que pour les series, lue dans l'autre sens : une
     * adaptation ne fait pas partie de la serie (un manga et son anime ne sont jamais compares)
     * mais elle dit combien de gens connaissent l'histoire. Voir {@link RecognitionScore}.
     */
    private Integer bestAdaptationPopularity(JsonNode node, WorkType type) {
        int best = 0;
        for (JsonNode edge : node.path("relations").path("edges")) {
            JsonNode related = edge.path("node");
            // Une adaptation change forcement d'univers : un anime tire d'un manga, ou l'inverse.
            if ("ADAPTATION".equals(edge.path("relationType").asText())
                    && !type.name().equals(related.path("type").asText())) {
                best = Math.max(best, related.path("popularity").asInt(0));
            }
        }
        return best;
    }

    /**
     * Liens de serie de cette oeuvre, filtres des l'ingestion.
     *
     * Deux filtres ici plutot qu'en aval : la nature de la relation (voir
     * {@link SeriesGrouper#MERGED_RELATION_TYPES}) et l'univers, car une adaptation relie un
     * manga a son anime, qui ne sont jamais compares l'un a l'autre.
     */
    private void collectRelations(JsonNode node, WorkType type, List<WorkRelation> into) {
        int from = node.path("id").asInt();
        for (JsonNode edge : node.path("relations").path("edges")) {
            String relationType = edge.path("relationType").asText();
            if (!SeriesGrouper.MERGED_RELATION_TYPES.contains(relationType)) {
                continue;
            }
            JsonNode related = edge.path("node");
            if (!type.name().equals(related.path("type").asText())) {
                continue;
            }
            into.add(new WorkRelation(from, related.path("id").asInt(), relationType));
        }
    }

    private MediaWork toWork(JsonNode node, WorkType type) {
        MediaWork work = new MediaWork();
        work.setAnilistId(node.path("id").asInt());
        work.setMalId(intOrNull(node, "idMal"));
        work.setType(type);

        JsonNode title = node.path("title");
        work.setTitleRomaji(textOrNull(title, "romaji"));
        work.setTitleEnglish(textOrNull(title, "english"));
        work.setTitleNative(textOrNull(title, "native"));

        work.setYear(resolveYear(node));
        work.setFormat(textOrNull(node, "format"));
        work.setStatus(textOrNull(node, "status"));
        work.setSource(textOrNull(node, "source"));
        work.setCountryOfOrigin(textOrNull(node, "countryOfOrigin"));
        work.setAverageScore(intOrNull(node, "averageScore"));
        work.setPopularity(intOrNull(node, "popularity"));
        work.setFavourites(intOrNull(node, "favourites"));
        work.setAdaptationPopularity(bestAdaptationPopularity(node, type));
        work.setEpisodes(intOrNull(node, "episodes"));
        work.setChapters(intOrNull(node, "chapters"));
        work.setVolumes(intOrNull(node, "volumes"));
        work.setDescription(stripHtml(textOrNull(node, "description")));
        work.setNsfw(node.path("isAdult").asBoolean(false));

        JsonNode cover = node.path("coverImage");
        work.setCoverUrl(textOrNull(cover, "extraLarge"));
        work.setCoverColor(textOrNull(cover, "color"));
        work.setBannerUrl(textOrNull(node, "bannerImage"));

        for (JsonNode genre : node.path("genres")) {
            work.getGenres().add(genre.asText());
        }

        addTitles(work, node);
        addTags(work, node);
        addCredits(work, node, type);
        addCharacters(work, node);
        addImages(work, node);

        return work;
    }

    /** Un anime porte seasonYear, un manga porte startDate.year. */
    private Integer resolveYear(JsonNode node) {
        Integer seasonYear = intOrNull(node, "seasonYear");
        if (seasonYear != null) {
            return seasonYear;
        }
        return intOrNull(node.path("startDate"), "year");
    }

    /**
     * Construit l'index des titres acceptes, en dedupliquant sur la forme normalisee :
     * chez AniList, romaji et english sont souvent identiques (« Berserk »).
     */
    private void addTitles(MediaWork work, JsonNode node) {
        Set<String> seen = new LinkedHashSet<>();

        addTitleIfNew(work, seen, work.getTitleRomaji(), TitleKind.ROMAJI);
        addTitleIfNew(work, seen, work.getTitleEnglish(), TitleKind.ENGLISH);
        addTitleIfNew(work, seen, work.getTitleNative(), TitleKind.NATIVE);

        for (JsonNode synonym : node.path("synonyms")) {
            addTitleIfNew(work, seen, synonym.asText(), TitleKind.SYNONYM);
        }
    }

    private void addTitleIfNew(MediaWork work, Set<String> seen, String value, TitleKind kind) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = TextNormalizer.normalize(value);
        // Un synonyme purement symbolique peut se normaliser en chaine vide : il serait inutilisable.
        if (normalized.isEmpty() || !seen.add(normalized)) {
            return;
        }
        work.addTitle(new MediaTitle(value.trim(), normalized, kind));
    }

    private void addTags(MediaWork work, JsonNode node) {
        for (JsonNode tag : node.path("tags")) {
            boolean spoiler = tag.path("isGeneralSpoiler").asBoolean(false)
                    || tag.path("isMediaSpoiler").asBoolean(false);
            work.addTag(new MediaTag(
                    tag.path("name").asText(),
                    intOrNull(tag, "rank"),
                    textOrNull(tag, "category"),
                    spoiler));
        }
    }

    /** Studio principal pour un anime, auteur pour un manga : AniList n'expose pas les deux. */
    private void addCredits(MediaWork work, JsonNode node, WorkType type) {
        if (type == WorkType.ANIME) {
            for (JsonNode studio : node.path("studios").path("nodes")) {
                work.addCredit(new MediaCredit(studio.path("name").asText(), CreditKind.STUDIO, null));
            }
            return;
        }

        for (JsonNode edge : node.path("staff").path("edges")) {
            String name = edge.path("node").path("name").path("full").asText(null);
            if (name != null && !name.isBlank()) {
                work.addCredit(new MediaCredit(name, CreditKind.AUTHOR, textOrNull(edge, "role")));
            }
        }
    }

    private void addCharacters(MediaWork work, JsonNode node) {
        for (JsonNode edge : node.path("characters").path("edges")) {
            JsonNode characterNode = edge.path("node");
            String fullName = characterNode.path("name").path("full").asText(null);
            String imageUrl = characterNode.path("image").path("large").asText(null);

            // Sans nom ni portrait, le personnage est inutilisable en jeu.
            if (fullName == null || fullName.isBlank() || imageUrl == null || imageUrl.isBlank()) {
                continue;
            }

            MediaCharacter character = new MediaCharacter();
            character.setAnilistId(intOrNull(characterNode, "id"));
            character.setName(fullName.trim());
            character.setNormalizedName(TextNormalizer.normalize(fullName));
            character.setImageUrl(imageUrl);
            character.setFavourites(intOrNull(characterNode, "favourites"));
            // Distingue l'oeuvre d'origine d'une apparition (voir CharacterHomes).
            character.setRole(textOrNull(edge, "role"));

            for (JsonNode alternative : characterNode.path("name").path("alternative")) {
                String value = alternative.asText(null);
                if (value != null && !value.isBlank()) {
                    character.getAlternativeNames().add(value.trim());
                }
            }

            work.addCharacter(character);
        }
    }

    /**
     * Images exploitables, par ordre de preference pour le mode Images :
     * vignettes d'episodes, puis banniere, puis jaquette.
     */
    private void addImages(MediaWork work, JsonNode node) {
        int ordinal = 0;
        Set<String> seenUrls = new LinkedHashSet<>();

        for (JsonNode episode : node.path("streamingEpisodes")) {
            String thumbnail = episode.path("thumbnail").asText(null);
            if (thumbnail != null && !thumbnail.isBlank() && seenUrls.add(thumbnail)) {
                work.addImage(new MediaImage(ImageKind.EPISODE_THUMB, thumbnail, ordinal++, null));
            }
        }

        if (work.getBannerUrl() != null) {
            work.addImage(new MediaImage(ImageKind.BANNER, work.getBannerUrl(), ordinal++, null));
        }

        if (work.getCoverUrl() != null) {
            work.addImage(new MediaImage(ImageKind.COVER, work.getCoverUrl(), ordinal, null));
        }
    }

    /** Les descriptions AniList contiennent du HTML leger meme avec asHtml:false. */
    private String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }
}
