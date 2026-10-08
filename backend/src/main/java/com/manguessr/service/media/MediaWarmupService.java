package com.manguessr.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rend les images d'une partie avant que le joueur ne les demande.
 *
 * Le premier appel a une image coute une seconde environ, et ce n'est pas le Pi : le CDN
 * de Crunchyroll met 0,8 s a repondre (mesure du 19/09/2026, dont 0,03 s de connexion et de
 * TLS), MangaDex 0,4 s ; le redimensionnement ajoute le reste. Les appels suivants lisent le
 * cache disque en 4 ms. Une manche de trois images faisait donc attendre le joueur trois fois.
 *
 * Le prechauffage s'execute en tache de fond pendant que le joueur regarde la premiere image :
 * les indices suivants et les manches suivantes sont prets a temps. Il est facultatif par
 * construction — un echec ne fait que ramener au comportement d'avant, jamais echouer une
 * partie — d'ou le catch large et la journalisation en debug, et d'ou l'interrupteur
 * {@code app.media.warmup-enabled} (coupe dans les tests, dont le catalogue pointe vers des
 * URLs fictives).
 */
@Service
public class MediaWarmupService {

    private static final Logger log = LoggerFactory.getLogger(MediaWarmupService.class);

    private final MediaProxyService proxyService;
    private final boolean enabled;

    public MediaWarmupService(MediaProxyService proxyService,
                              @Value("${app.media.warmup-enabled:true}") boolean enabled) {
        this.proxyService = proxyService;
        this.enabled = enabled;
    }

    /**
     * Prepare les images citees, une par une.
     *
     * Sequentiel a dessein : sur un Raspberry Pi, lancer dix redimensionnements de front
     * ralentirait la requete que le joueur attend vraiment.
     */
    @Async
    public void warm(List<MediaToken> tokens) {
        if (!enabled) {
            return;
        }
        for (MediaToken token : tokens) {
            try {
                proxyService.render(token);
            } catch (RuntimeException e) {
                log.debug("Prechauffage impossible pour {} {} : {}",
                        token.source(), token.id(), e.getMessage());
            }
        }
    }
}
