package com.manguessr.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Client GraphQL AniList.
 *
 * <h3>Pourquoi ces en-tetes sont obligatoires</h3>
 * AniList filtre les requetes sans <b>Referer</b> : sans lui, l'API repond 403 avec le message
 * « The AniList API has been temporarily disabled due to severe stability issues », qui laisse
 * croire a une panne globale alors que le service fonctionne. La valeur du Referer est
 * indifferente, seule sa presence compte. Le User-Agent {@code Python-urllib/*} est en outre
 * sur liste noire, d'ou un UA explicite.
 *
 * Matrice verifiee le 11/09/2026, 5 essais par combinaison :
 * <pre>
 *   UA curl / manguessr / Mozilla  + sans Referer -> 403
 *   UA curl / manguessr / Mozilla  + avec Referer -> 200
 *   UA Python-urllib               + avec Referer -> 403
 * </pre>
 *
 * Ne pas conclure a une panne AniList sur un 403 sans avoir verifie ces deux en-tetes.
 */
@Component
public class AniListClient {

    private static final Logger log = LoggerFactory.getLogger(AniListClient.class);

    private static final String ENDPOINT = "https://graphql.anilist.co";
    private static final int MAX_ATTEMPTS = 4;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final String userAgent;
    private final String referer;

    public AniListClient(ObjectMapper objectMapper,
                         @Value("${app.catalog.anilist.user-agent:manguessr/1.0}") String userAgent,
                         @Value("${app.catalog.anilist.referer:https://anilist.co/}") String referer,
                         @Value("${app.catalog.anilist.requests-per-minute:30}") int requestsPerMinute) {
        this.objectMapper = objectMapper;
        this.userAgent = userAgent;
        this.referer = referer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.rateLimiter = new RateLimiter("anilist",
                Duration.ofMillis(Math.max(1, 60_000 / Math.max(1, requestsPerMinute))));
    }

    /**
     * Execute une requete GraphQL et renvoie le contenu de {@code data}.
     *
     * @throws CatalogHttpException si l'appel echoue apres toutes les tentatives,
     *                              ou si la reponse contient des erreurs GraphQL
     */
    public JsonNode query(String query, Map<String, Object> variables) {
        String body = serializeRequest(query, variables);

        CatalogHttpException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                HttpResponse<String> response = send(body);
                int status = response.statusCode();

                if (status == 200) {
                    return extractData(response.body());
                }

                if (status == 429) {
                    // AniList indique combien de temps patienter ; on le respecte au lieu de reessayer a l'aveugle.
                    rateLimiter.backOff(retryAfter(response).orElse(Duration.ofSeconds(60)));
                    lastFailure = new CatalogHttpException("AniList : quota depasse (429)", status);
                    continue;
                }

                if (status == 403) {
                    // Cause quasi certaine : en-tetes manquants. Inutile de reessayer, le message doit etre explicite.
                    throw new CatalogHttpException(
                            "AniList a repondu 403. Verifier les en-tetes Referer et User-Agent "
                                    + "(voir la javadoc d'AniListClient) avant de conclure a une panne.", status);
                }

                if (status >= 500) {
                    lastFailure = new CatalogHttpException("AniList : erreur serveur " + status, status);
                    backOffExponentially(attempt);
                    continue;
                }

                throw new CatalogHttpException("AniList : reponse inattendue " + status, status);

            } catch (IOException e) {
                lastFailure = new CatalogHttpException("AniList : echec reseau", e);
                backOffExponentially(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CatalogHttpException("AniList : appel interrompu", e);
            }
        }

        throw lastFailure != null
                ? lastFailure
                : new CatalogHttpException("AniList : echec apres " + MAX_ATTEMPTS + " tentatives", 0);
    }

    private HttpResponse<String> send(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // Les deux en-tetes qui conditionnent l'acces — voir la javadoc de la classe.
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.REFERER, referer)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String serializeRequest(String query, Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(Map.of("query", query, "variables", variables));
        } catch (Exception e) {
            throw new CatalogHttpException("AniList : serialisation de la requete impossible", e);
        }
    }

    private JsonNode extractData(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errors = root.get("errors");
            if (errors != null && errors.isArray() && !errors.isEmpty()) {
                String message = errors.get(0).path("message").asText("erreur GraphQL inconnue");
                throw new CatalogHttpException("AniList : " + message, 200);
            }
            return root.path("data");
        } catch (CatalogHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new CatalogHttpException("AniList : reponse JSON illisible", e);
        }
    }

    private java.util.Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Duration.ofSeconds(Long.parseLong(value.trim()));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    private void backOffExponentially(int attempt) {
        long delayMs = (long) Math.pow(2, attempt) * 1000L;
        log.warn("AniList : tentative {}/{} echouee, nouvel essai dans {} ms", attempt, MAX_ATTEMPTS, delayMs);
        rateLimiter.backOff(Duration.ofMillis(delayMs));
    }
}
