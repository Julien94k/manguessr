package com.manguessr.controller;

import com.manguessr.model.dto.LeaderboardView;
import com.manguessr.model.enums.WorkType;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.service.stats.LeaderboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Classement public ; la ligne du joueur connecte est ajoutee quand il y figure. */
@RestController
@RequestMapping("/api")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * @param period daily, weekly ou alltime
     * @param theme  anime, manga, ou all pour les deux univers
     */
    @GetMapping("/leaderboard")
    public LeaderboardView leaderboard(@RequestParam(defaultValue = "daily") String period,
                                       @RequestParam(defaultValue = "all") String theme,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        WorkType workType = "all".equalsIgnoreCase(theme.trim()) ? null : WorkType.fromParam(theme);
        return leaderboardService.leaderboard(period, workType, principal == null ? null : principal.getUser());
    }
}
