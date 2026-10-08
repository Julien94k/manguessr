package com.manguessr.controller;

import com.manguessr.model.dto.*;
import com.manguessr.model.entity.User;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.service.game.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * Pilotage d'une partie.
 *
 * Rien dans les reponses de ce controleur ne permet de retrouver l'oeuvre a deviner avant
 * la fin d'une manche : les vues sont construites par les handlers de mode, qui portent
 * cette responsabilite.
 */
@RestController
@RequestMapping("/api")
public class GameController {

    private final GameSessionService sessionService;

    public GameController(GameSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Modes disponibles dans un univers, avec leur jouabilite au vu du catalogue. */
    @GetMapping("/games")
    public List<GameModeView> games(@RequestParam(defaultValue = "anime") String theme) {
        return sessionService.availableModes(parseTheme(theme));
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionView> start(@Valid @RequestBody StartSessionRequest request,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        SessionView session = sessionService.start(
                userOrNull(principal),
                parseMode(request.mode()),
                parseTheme(request.theme()),
                request.unlimited());

        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions/{id}")
    public SessionView get(@PathVariable Long id,
                           @AuthenticationPrincipal CustomUserDetails principal) {
        return sessionService.get(id, userOrNull(principal));
    }

    @PostMapping("/sessions/{id}/guess")
    public GuessResponse guess(@PathVariable Long id,
                               @Valid @RequestBody GuessRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        return sessionService.guess(id, userOrNull(principal), request);
    }

    @PostMapping("/sessions/{id}/clue")
    public RoundView clue(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int round,
                          @AuthenticationPrincipal CustomUserDetails principal) {
        return sessionService.unlockClue(id, userOrNull(principal), round);
    }

    @PostMapping("/sessions/{id}/skip")
    public SessionView skip(@PathVariable Long id,
                            @RequestParam(defaultValue = "0") int round,
                            @AuthenticationPrincipal CustomUserDetails principal) {
        return sessionService.skip(id, userOrNull(principal), round);
    }

    /** Une partie anonyme est rattachee a aucun joueur : jouable, mais hors classement. */
    private User userOrNull(CustomUserDetails principal) {
        return principal == null ? null : principal.getUser();
    }

    private WorkType parseTheme(String value) {
        try {
            return WorkType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Univers inconnu : '" + value + "'. Attendu : anime ou manga.");
        }
    }

    private GameMode parseMode(String value) {
        try {
            return GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Mode inconnu : '" + value + "'.");
        }
    }
}
