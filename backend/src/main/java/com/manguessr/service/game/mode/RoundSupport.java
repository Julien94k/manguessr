package com.manguessr.service.game.mode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;
import com.manguessr.model.enums.SessionStatus;
import com.manguessr.service.media.OpaqueTokenService;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Briques communes aux modes : serialisation du contenu d'une manche, construction des URLs
 * media signees et revelation de l'oeuvre.
 *
 * Centralise deux invariants qu'il ne faut surtout pas reimplementer mode par mode :
 * une URL media n'est jamais servie en clair, et la solution n'est construite que sur une
 * manche fermee.
 */
@Component
public class RoundSupport {

    private final ObjectMapper objectMapper;
    private final OpaqueTokenService tokenService;

    public RoundSupport(ObjectMapper objectMapper, OpaqueTokenService tokenService) {
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
    }

    public String serialize(RoundPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Serialisation du contenu de manche impossible", e);
        }
    }

    public RoundPayload deserialize(SessionRound round) {
        return deserialize(round.getPayload());
    }

    /** Meme lecture depuis le texte brut : un puzzle quotidien porte ses manches hors session. */
    public RoundPayload deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return RoundPayload.empty();
        }
        try {
            return objectMapper.readValue(payload, RoundPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Contenu de manche illisible", e);
        }
    }

    /** URL proxifiee d'une image du catalogue, signee pour un traitement et un palier donnes. */
    public String imageUrl(long imageId, MediaTransform transform, int parameter) {
        return "/api/media/img/" + tokenService.sign(MediaSource.IMAGE, imageId, transform, parameter);
    }

    /** URL proxifiee d'un portrait de personnage. */
    public String characterUrl(long characterId) {
        return "/api/media/img/"
                + tokenService.sign(MediaSource.CHARACTER, characterId, MediaTransform.RAW, 0);
    }

    /**
     * URL proxifiee d'une piste de generique. Les fichiers d'origine portent le titre de
     * l'anime dans leur nom : ils ne sont jamais servis en clair.
     */
    public String themeUrl(long themeId, MediaSource track) {
        if (!track.isStream()) {
            throw new IllegalArgumentException("Source de generique attendue : " + track);
        }
        return "/api/media/stream/" + tokenService.sign(track, themeId, MediaTransform.RAW, 0);
    }

    /**
     * Masque un titre en ne laissant que l'initiale de chaque mot.
     *
     * Reproduit l'indice titre d'AniGuessr : « Fire Force » devient « F___ F____ ».
     * Un mot d'une seule lettre est entierement masque, sinon l'indice donnerait le mot.
     */
    public String maskTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        StringBuilder masked = new StringBuilder();
        for (String word : title.trim().split("\\s+")) {
            if (!masked.isEmpty()) {
                masked.append("  ");
            }
            if (word.length() == 1) {
                masked.append('_');
                continue;
            }
            masked.append(word.charAt(0));
            masked.append("_".repeat(word.length() - 1));
        }
        return masked.toString();
    }

    /**
     * Construit la revelation de fin de manche.
     *
     * @return {@code null} si la manche est encore ouverte — le garde-fou qui empeche
     *         la reponse de fuiter
     */
    public RoundView.SolutionView solutionIfClosed(SessionRound round) {
        if (round.getStatus() == SessionStatus.IN_PROGRESS) {
            return null;
        }
        return solution(round.getWork(), null, null);
    }

    public RoundView.SolutionView solution(MediaWork work, String songTitle, String artist) {
        String title = work.displayTitle();
        // Titre principal en anglais : le romaji devient le titre secondaire.
        String alternative = work.getTitleRomaji();
        if (alternative != null && alternative.equalsIgnoreCase(title)) {
            alternative = null;
        }

        return new RoundView.SolutionView(
                title,
                alternative,
                work.getYear(),
                work.getCoverUrl(),
                "https://anilist.co/" + work.getType().name().toLowerCase(Locale.ROOT)
                        + "/" + work.getAnilistId(),
                songTitle,
                artist);
    }
}
