package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manguessr.model.entity.MediaTheme;
import com.manguessr.model.enums.ThemeKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client AnimeThemes.moe : openings et endings, absents d'AniList.
 *
 * Le rapprochement se fait directement par identifiant AniList grace au filtre
 * {@code filter[site]=AniList&filter[external_id]=...} — aucune correspondance de titres.
 */
@Component
public class AnimeThemesClient {

    private static final String API = "https://api.animethemes.moe";

    private final JsonHttpClient http;

    public AnimeThemesClient(ObjectMapper objectMapper,
                             @Value("${app.catalog.animethemes.user-agent:manguessr/1.0}") String userAgent,
                             @Value("${app.catalog.animethemes.requests-per-second:4}") int requestsPerSecond) {
        this.http = new JsonHttpClient(objectMapper, "animethemes", userAgent,
                Duration.ofMillis(Math.max(1, 1000 / Math.max(1, requestsPerSecond))));
    }

    /**
     * Generiques d'un anime, identifie par son identifiant AniList.
     *
     * @return les themes detaches, sans oeuvre rattachee ; vide si l'anime est inconnu
     */
    public List<MediaTheme> fetchThemes(int anilistId) {
        String url = API + "/anime"
                + "?filter[has]=resources"
                + "&filter[site]=AniList"
                + "&filter[external_id]=" + anilistId
                + "&include=animethemes.animethemeentries.videos.audio,animethemes.song"
                + "&page[size]=1";

        JsonNode response = http.get(url);
        if (response == null) {
            return List.of();
        }

        JsonNode animeArray = response.path("anime");
        if (!animeArray.isArray() || animeArray.isEmpty()) {
            return List.of();
        }

        List<MediaTheme> themes = new ArrayList<>();
        for (JsonNode themeNode : animeArray.get(0).path("animethemes")) {
            String type = themeNode.path("type").asText(null);
            if (!"OP".equals(type) && !"ED".equals(type)) {
                continue;
            }

            JsonNode video = firstVideo(themeNode);
            if (video == null) {
                continue;
            }

            String videoUrl = video.path("link").asText(null);
            // Lien audio officiel, et non reconstruit : le nom du fichier audio differe souvent
            // de celui de la video (100Man-OP1.webm -> 100Man-OP1-NCBD1080.ogg). Reconstruire le
            // chemin depuis la video donnait un 404 sur environ un generique sur quatre.
            String audioUrl = video.path("audio").path("link").asText(null);
            if (videoUrl == null || audioUrl == null) {
                continue;
            }

            MediaTheme theme = new MediaTheme();
            theme.setKind(ThemeKind.valueOf(type));
            theme.setSequence(themeNode.path("sequence").asInt(1));
            theme.setSongTitle(themeNode.path("song").path("title").asText(null));
            theme.setArtist(themeNode.path("song").path("artists").isArray()
                    && !themeNode.path("song").path("artists").isEmpty()
                    ? themeNode.path("song").path("artists").get(0).path("name").asText(null)
                    : null);
            theme.setVideoUrl(videoUrl);
            theme.setAudioUrl(audioUrl);

            themes.add(theme);
        }

        return themes;
    }

    private JsonNode firstVideo(JsonNode themeNode) {
        for (JsonNode entry : themeNode.path("animethemeentries")) {
            for (JsonNode video : entry.path("videos")) {
                return video;
            }
        }
        return null;
    }
}
