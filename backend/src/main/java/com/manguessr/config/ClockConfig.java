package com.manguessr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Horloge injectee plutot qu'appelee en statique : le jour de jeu bascule a minuit UTC,
 * et les tests doivent pouvoir se placer a une date donnee sans attendre.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
