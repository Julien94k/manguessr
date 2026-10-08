package com.manguessr.repository;

import com.manguessr.model.entity.DailyPuzzle;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPuzzleRepository extends JpaRepository<DailyPuzzle, Long> {

    boolean existsByPuzzleDateAndModeAndTheme(LocalDate puzzleDate, GameMode mode, WorkType theme);

    /** Puzzle avec ses manches et leurs oeuvres, prets a etre copies dans une partie. */
    @Query("select distinct p from DailyPuzzle p left join fetch p.rounds r left join fetch r.work "
            + "where p.puzzleDate = :date and p.mode = :mode and p.theme = :theme")
    Optional<DailyPuzzle> findWithRounds(@Param("date") LocalDate date,
                                         @Param("mode") GameMode mode,
                                         @Param("theme") WorkType theme);

    /**
     * Oeuvres deja tirees dans ce mode et cet univers autour d'une date, cette date exclue.
     *
     * La fenetre couvre aussi les jours suivants : le puzzle du lendemain est genere avant
     * minuit, et le suivant ne doit pas le repeter.
     */
    @Query("select r.work.id from PuzzleRound r where r.puzzle.mode = :mode and r.puzzle.theme = :theme "
            + "and r.puzzle.puzzleDate between :from and :to and r.puzzle.puzzleDate <> :date")
    List<Long> findWorkIdsUsedAround(@Param("mode") GameMode mode,
                                     @Param("theme") WorkType theme,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("date") LocalDate date);
}
