package com.manguessr.controller;

import com.manguessr.service.media.MediaProxyService;
import com.manguessr.service.media.MediaStreamService;
import com.manguessr.service.media.MediaToken;
import com.manguessr.service.media.OpaqueTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Sert les medias du jeu : images rendues cote serveur et generiques relayes en flux.
 *
 * L'acces est public parce que le jeton porte lui-meme son autorisation : signe en HMAC,
 * il fixe l'image, le traitement et le palier, et expire. Sans jeton valide, rien n'est servi.
 */
@RestController
@RequestMapping("/api/media")
public class MediaProxyController {

    private static final Logger log = LoggerFactory.getLogger(MediaProxyController.class);

    private final OpaqueTokenService tokenService;
    private final MediaProxyService proxyService;
    private final MediaStreamService streamService;

    public MediaProxyController(OpaqueTokenService tokenService,
                                MediaProxyService proxyService,
                                MediaStreamService streamService) {
        this.tokenService = tokenService;
        this.proxyService = proxyService;
        this.streamService = streamService;
    }

    @GetMapping(value = "/img/{token}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String token) {
        MediaToken verified = tokenService.verify(token);
        byte[] bytes = proxyService.render(verified);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                // Le rendu d'un jeton donne est immuable : le navigateur peut le garder.
                // "private" evite qu'un cache partage serve une image a un autre joueur.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(bytes);
    }

    /**
     * Relaie un generique (audio ou clip). L'en-tete Range est transmis a la source, pour
     * que le lecteur du navigateur puisse avancer dans la piste.
     *
     * Le relais est volontairement <b>synchrone</b>. En {@code StreamingResponseBody}, le
     * lecteur qui abandonne une plage pour en demander une autre faisait recycler la requete
     * par Tomcat pendant que le thread asynchrone ecrivait encore : NullPointerException dans
     * Tomcat, puis 500 ecrit sur une reponse deja recyclee.
     */
    @GetMapping("/stream/{token}")
    public void stream(@PathVariable String token,
                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                       HttpServletResponse response) {

        // Verification et ouverture avant toute ecriture : un jeton invalide ou une source en
        // panne passent encore par GlobalExceptionHandler, avec un vrai code d'erreur.
        MediaToken verified = tokenService.verify(token);
        MediaStreamService.UpstreamStream upstream = streamService.open(verified, range);

        try (InputStream input = upstream.body()) {
            response.setStatus(upstream.status());
            upstream.headers().forEach(response::setHeader);
            response.setHeader(HttpHeaders.CACHE_CONTROL,
                    CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().getHeaderValue());
            input.transferTo(response.getOutputStream());
        } catch (IOException e) {
            // Le navigateur interrompt couramment une plage (saut dans la piste, changement de
            // page) : ce n'est pas une erreur, et la reponse est deja engagee de toute facon.
            log.debug("Relais de generique interrompu : {}", e.getMessage());
        }
    }
}
