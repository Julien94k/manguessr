package com.manguessr.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache en memoire, suffisant ici : les donnees mises en cache sont petites, immuables entre
 * deux ingestions, et l'application tourne en instance unique.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Jouabilite des modes (`GET /api/games`).
     *
     * Sans ce cache, l'accueil coutait <b>1,6 s</b> a chaque visite : la jouabilite se calcule en
     * listant, mode par mode et palier par palier, toutes les oeuvres eligibles — une quinzaine
     * de requetes a sous-requete correlee sur 2000 oeuvres, pour ne regarder que si le resultat
     * est vide. Elle ne change qu'a une ingestion, qui vide donc ce cache.
     */
    public static final String GAME_MODES = "game-modes";

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "autocomplete-titles", "autocomplete-characters", GAME_MODES);
    }
}
