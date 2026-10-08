package com.manguessr.controller;

import com.manguessr.model.dto.DailyOverviewView;
import com.manguessr.model.enums.WorkType;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.service.stats.PlayerStatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Defi du jour : public, avec la progression du joueur quand il est connecte. */
@RestController
@RequestMapping("/api")
public class DailyController {

    private final PlayerStatsService statsService;

    public DailyController(PlayerStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/daily")
    public DailyOverviewView daily(@RequestParam(defaultValue = "anime") String theme,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return statsService.dailyOverview(WorkType.fromParam(theme), principal == null ? null : principal.getUser());
    }
}
