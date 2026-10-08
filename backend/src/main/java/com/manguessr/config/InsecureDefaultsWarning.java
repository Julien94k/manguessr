package com.manguessr.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Signale au demarrage un secret JWT reste a sa valeur d'exemple.
 *
 * Un simple avertissement plutot qu'un refus de demarrer : les tests et le dev local tournent
 * volontairement avec la valeur par defaut. Mais en production, quiconque a lu .env.sample
 * pourrait forger un jeton administrateur.
 */
@Component
public class InsecureDefaultsWarning {

    private static final Logger log = LoggerFactory.getLogger(InsecureDefaultsWarning.class);

    private final String jwtSecret;

    public InsecureDefaultsWarning(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnOnInsecureDefaults() {
        if (jwtSecret.startsWith("change_me")) {
            log.warn("JWT_SECRET est une valeur d'exemple : n'importe qui peut forger un jeton. "
                    + "Generer un secret (openssl rand -base64 48) avant toute mise en ligne.");
        }
    }
}
