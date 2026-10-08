package com.manguessr.service.game;

import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.DailyPuzzleRepository;
import com.manguessr.service.game.mode.GameModeHandler;
import com.manguessr.service.media.MediaToken;
import com.manguessr.service.media.MediaWarmupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prechauffage du puzzle quotidien.
 *
 * Le scheduler genere J et J+1 a 00:05 UTC, quand personne ne joue : c'est le bon moment pour
 * rendre les images. Sans cela, le premier joueur de la journee attendait ~1 s par image —
 * le temps de reponse du CDN d'origine — et remplissait le cache pour les suivants.
 */
class DailyPuzzleGeneratorTest {

    private static final MediaToken TOKEN =
            MediaToken.forRender(MediaSource.IMAGE, 42L, MediaTransform.RAW, 0);

    private DailyPuzzleRepository puzzleRepository;
    private MediaWarmupService warmupService;
    private DailyPuzzleGenerator generator;

    @BeforeEach
    void setUp() {
        puzzleRepository = mock(DailyPuzzleRepository.class);
        warmupService = mock(MediaWarmupService.class);
        RoundDrafter drafter = mock(RoundDrafter.class);

        GameModeHandler handler = mock(GameModeHandler.class);
        when(handler.mode()).thenReturn(GameMode.IMAGES);
        when(handler.mediaToWarm(any(MediaWork.class), anyString())).thenReturn(List.of(TOKEN));

        MediaWork work = new MediaWork();
        work.setAnilistId(1);
        work.setType(WorkType.MANGA);
        when(drafter.draft(any(), any(), anyLong(), anySet()))
                .thenReturn(List.of(new RoundDrafter.Draft(0, Difficulty.EASY, work, "{}")));

        generator = new DailyPuzzleGenerator(List.of(handler), puzzleRepository, drafter,
                warmupService, mock(PlatformTransactionManager.class), 180);
    }

    @Test
    void un_puzzle_genere_fait_preparer_ses_images() {
        // Le mock renvoie false : le puzzle du jour n'existe pas encore.
        generator.ensure(LocalDate.of(2031, 1, 1), GameMode.IMAGES, WorkType.MANGA);

        verify(warmupService).warm(List.of(TOKEN));
    }

    @Test
    void un_puzzle_deja_genere_ne_relance_rien() {
        // Idempotence : le scheduler repasse sur J+1 le lendemain, et une partie reprise non plus
        // ne doit pas relancer de rendu — les images sont deja en cache.
        when(puzzleRepository.existsByPuzzleDateAndModeAndTheme(
                any(LocalDate.class), any(GameMode.class), any(WorkType.class))).thenReturn(true);

        generator.ensure(LocalDate.of(2031, 1, 1), GameMode.IMAGES, WorkType.MANGA);

        verify(warmupService, never()).warm(any());
    }
}
