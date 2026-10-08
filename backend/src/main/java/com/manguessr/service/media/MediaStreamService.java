package com.manguessr.service.media;

import com.manguessr.model.entity.MediaTheme;
import com.manguessr.model.enums.MediaSource;
import com.manguessr.repository.MediaThemeRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Relaie en flux les generiques des modes Opening et Ending.
 *
 * Les fichiers d'AnimeThemes portent le titre dans leur nom
 * ({@code DarlingInTheFranXX-OP1.ogg}) : servir l'URL d'origine donnerait la reponse. Le
 * serveur les relaie donc derriere un jeton signe, comme les images.
 *
 * Contrairement aux images, rien n'est mis en cache : un clip pese plusieurs dizaines de
 * mega-octets et le relais n'applique aucun traitement couteux. L'en-tete {@code Range} est
 * transmis, sans quoi le navigateur ne pourrait pas avancer dans la piste.
 */
@Service
public class MediaStreamService {

    /** En-tetes amont utiles au lecteur du navigateur ; les autres (cookies, serveur) sont ecartes. */
    private static final List<String> FORWARDED_HEADERS =
            List.of("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges");

    /** Seule forme de Range transmise : un intervalle d'octets simple, pas de multi-plages. */
    private static final Pattern BYTE_RANGE = Pattern.compile("bytes=\\d*-\\d*");

    private final MediaThemeRepository themeRepository;
    private final HttpClient httpClient;

    public MediaStreamService(MediaThemeRepository themeRepository) {
        this.themeRepository = themeRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Flux amont ouvert. L'appelant doit fermer {@code body}.
     *
     * @param status  statut a renvoyer au client (200, ou 206 pour une plage)
     * @param headers en-tetes a recopier
     * @param body    contenu
     */
    public record UpstreamStream(int status, Map<String, String> headers, InputStream body) {}

    /**
     * Ouvre le generique designe par un jeton deja verifie.
     *
     * @param range en-tete Range du client, ou {@code null}
     */
    public UpstreamStream open(MediaToken token, String range) {
        String url = resolveSourceUrl(token);

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "manguessr/1.0")
                .GET();
        if (range != null && BYTE_RANGE.matcher(range.trim()).matches()) {
            request.header("Range", range.trim());
        }

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Source du générique injoignable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Relais du générique interrompu.");
        }

        int status = response.statusCode();
        if (status >= 400) {
            closeQuietly(response.body());
            throw new IllegalStateException("Générique indisponible chez la source (HTTP " + status + ").");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : FORWARDED_HEADERS) {
            response.headers().firstValue(name).ifPresent(value -> headers.put(name, value));
        }
        return new UpstreamStream(status, headers, response.body());
    }

    private String resolveSourceUrl(MediaToken token) {
        MediaTheme theme = switch (token.source()) {
            case THEME_AUDIO, THEME_VIDEO -> themeRepository.findById(token.id())
                    .orElseThrow(() -> new IllegalArgumentException("Générique introuvable."));
            case IMAGE, CHARACTER -> throw new IllegalArgumentException("Ce jeton ne désigne pas un générique.");
        };

        String url = token.source() == MediaSource.THEME_AUDIO
                ? theme.getAudioUrl()
                : theme.getVideoUrl();
        if (url == null) {
            throw new IllegalArgumentException("Piste absente pour ce générique.");
        }
        return url;
    }

    private void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Flux d'erreur : rien a recuperer.
        }
    }
}
