package com.manguessr.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.Objects;

/**
 * Centralise la gestion des erreurs : les controleurs restent fins et ne portent aucun try/catch.
 *
 * Toutes les reponses d'erreur ont la meme forme {"error": "..."}, celle que le
 * front attend dans l'intercepteur d'apiClient.ts.
 *
 * On etend {@link ResponseEntityExceptionHandler} pour que les exceptions de Spring MVC
 * (route inconnue, methode non supportee, media type invalide...) conservent leur propre
 * statut HTTP au lieu d'etre transformees en 500 par le filet de securite.
 *
 * Les details internes (stack traces, noms de classes) ne sont jamais renvoyes au client,
 * ils sont uniquement logges cote serveur.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /** Erreurs metier explicites levees par les services. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Requete invalide : {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Etat de jeu incoherent : manche deja terminee, essais epuises, session d'un autre joueur. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.warn("Etat invalide : {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        // Message volontairement identique que le compte existe ou non : pas d'enumeration d'emails.
        return error(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Acces refuse.");
    }

    /**
     * Course entre deux inscriptions simultanees sur le meme email ou pseudo : la contrainte
     * d'unicite en base tranche, on la traduit en 409 plutot qu'en 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violation de contrainte d'integrite : {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "Cet email ou ce pseudo vient d'etre pris.");
    }

    /** Violations de @Valid : on remonte le premier message de contrainte, deja lisible. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Champ invalide"))
                .orElse("Requete invalide.");
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /**
     * Uniformise le corps de toutes les exceptions gerees par la classe parente
     * (404 route inconnue, 405 methode non supportee, 415 media type...).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            log.error("Erreur Spring MVC : {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(status)
                .headers(headers)
                .body(Map.of("error", status.getReasonPhrase()));
    }

    /** Filet de securite : le message interne est logge mais jamais expose. */
    /**
     * Le client a ferme la connexion en cours de reponse (image abandonnee en changeant de page,
     * saut dans un generique). Rien a repondre : tenter d'ecrire un corps d'erreur sur une
     * connexion fermee echouerait a son tour, et journaliser en ERROR noierait les vraies pannes.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Client deconnecte pendant la reponse : {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        log.error("Erreur non geree : {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue s'est produite.");
    }
}
