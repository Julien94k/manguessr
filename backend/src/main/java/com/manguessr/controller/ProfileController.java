package com.manguessr.controller;

import com.manguessr.model.dto.PlayerStatsView;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.service.stats.PlayerStatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Statistiques du joueur connecte. Route authentifiee (regle par defaut de SecurityConfig). */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final PlayerStatsService statsService;

    public ProfileController(PlayerStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public PlayerStatsView stats(@AuthenticationPrincipal CustomUserDetails principal) {
        return statsService.statsFor(principal.getUser());
    }
}
