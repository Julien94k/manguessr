package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Petit client GET JSON partage par les sources REST (MangaDex, AnimeThemes).
 *
 * Applique l'etalement des appels, retente sur 429 et 5xx, et renvoie {@code null}
 * sur 404 — un manga sans couverture n'est pas une erreur d'ingestion.
 */
public class JsonHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JsonHttpClient.class);
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final String userAgent;
    private final String sourceName;

    public JsonHttpClient(ObjectMapper objectMapper, String sourceName, String userAgent, Duration minInterval) {
        this.objectMapper = objectMapper;
        this.sourceName = sourceName;
        this.userAgent = userAgent;
        this.rateLimiter = new RateLimiter(sourceName, minInterval);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * @return le corps JSON, ou {@code null} si la ressource n'existe pas (404)
     * @throws CatalogHttpException si l'appel echoue apres toutes les tentatives
     */
    public JsonNode get(String url) {
        CatalogHttpException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status == 200) {
                    return objectMapper.readTree(response.body());
                }
                if (status == 404) {
                    return null;
                }
                if (status == 429 || status >= 500) {
                    lastFailure = new CatalogHttpException(sourceName + " : statut " + status, status);
                    long delayMs = (long) Math.pow(2, attempt) * 1000L;
                    log.warn("{} : statut {} sur {}, nouvel essai dans {} ms", sourceName, status, url, delayMs);
                    rateLimiter.backOff(Duration.ofMillis(delayMs));
                    continue;
                }

                throw new CatalogHttpException(sourceName + " : reponse inattendue " + status, status);

            } catch (IOException e) {
                lastFailure = new CatalogHttpException(sourceName + " : echec reseau", e);
                rateLimiter.backOff(Duration.ofMillis((long) Math.pow(2, attempt) * 1000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CatalogHttpException(sourceName + " : appel interrompu", e);
            }
        }

        throw lastFailure != null
                ? lastFailure
                : new CatalogHttpException(sourceName + " : echec apres " + MAX_ATTEMPTS + " tentatives", 0);
    }
}
