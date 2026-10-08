package com.manguessr.repository;

import com.manguessr.model.entity.GameSession;
import com.manguessr.model.entity.User;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.SessionStatus;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.projection.ModeAggregate;
import com.manguessr.repository.projection.ScoreAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    /**
     * Partie quotidienne deja entamee par un joueur sur un mode donne.
     *
     * Garantit qu'on ne rejoue pas le puzzle du jour : on reprend la partie en cours.
     */
    Optional<GameSession> findByUserAndModeAndThemeAndPlayDateAndUnlimitedFalse(
            User user, GameMode mode, WorkType theme, LocalDate playDate);

    List<GameSession> findByUserAndPlayDateAndUnlimitedFalse(User user, LocalDate playDate);

    /** Charge une partie avec ses manches et leurs oeuvres, sans dependre d'un proxy paresseux. */
    @Query("select distinct s from GameSession s "
            + "left join fetch s.rounds r left join fetch r.work where s.id = :id")
    Optional<GameSession> findByIdWithRounds(@Param("id") Long id);

    /** Jours ou le joueur a termine au moins une partie quotidienne : la base de la serie. */
    @Query("select distinct s.playDate from GameSession s "
            + "where s.user = :user and s.unlimited = false and s.status = :status")
    List<LocalDate> findDailyPlayDates(@Param("user") User user, @Param("status") SessionStatus status);

    @Query("select new com.manguessr.repository.projection.ModeAggregate("
            + "s.mode, s.theme, count(s), sum(s.totalScore), max(s.totalScore), avg(s.totalScore)) "
            + "from GameSession s where s.user = :user and s.unlimited = false and s.status = :status "
            + "group by s.mode, s.theme")
    List<ModeAggregate> aggregateDailyByMode(@Param("user") User user, @Param("status") SessionStatus status);

    List<GameSession> findTop10ByUserAndUnlimitedFalseAndStatusOrderByPlayDateDescIdDesc(
            User user, SessionStatus status);

    /**
     * Classement toutes univers confondus. Seules les parties quotidiennes comptent : en
     * illimite, rejouer jusqu'a tomber sur une oeuvre connue rendrait le classement absurde.
     */
    @Query("select new com.manguessr.repository.projection.ScoreAggregate("
            + "u.id, u.username, sum(s.totalScore), count(s)) "
            + "from GameSession s join s.user u "
            + "where s.unlimited = false and s.playDate between :from and :to "
            + "group by u.id, u.username order by sum(s.totalScore) desc, count(s) asc")
    List<ScoreAggregate> leaderboard(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.manguessr.repository.projection.ScoreAggregate("
            + "u.id, u.username, sum(s.totalScore), count(s)) "
            + "from GameSession s join s.user u "
            + "where s.unlimited = false and s.theme = :theme and s.playDate between :from and :to "
            + "group by u.id, u.username order by sum(s.totalScore) desc, count(s) asc")
    List<ScoreAggregate> leaderboardForTheme(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("theme") WorkType theme);
}
