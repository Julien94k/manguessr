package com.manguessr.repository;

import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.entity.User;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionRoundRepository extends JpaRepository<SessionRound, Long> {

    /**
     * Oeuvres vues recemment par un joueur dans ses parties libres d'un mode et d'un univers.
     *
     * Une partie libre tire a neuf dans tout le vivier : sans cette memoire, les repetitions
     * arrivent vite (le mode Images manga ne comptait que 126 oeuvres jouables). Chaque mode en tire ce qui le
     * concerne ({@code GameModeHandler.recall}) : le mode Personnages y lit aussi ses portraits. Le puzzle
     * quotidien a sa propre regle, bien plus longue, dans {@code DailyPuzzleGenerator}.
     */
    @Query("select r from SessionRound r join fetch r.work where r.session.user = :user "
            + "and r.session.mode = :mode and r.session.theme = :theme "
            + "and r.session.unlimited = true order by r.session.id desc")
    List<SessionRound> findRecentUnlimitedRounds(@Param("user") User user,
                                                 @Param("mode") GameMode mode,
                                                 @Param("theme") WorkType theme,
                                                 Pageable pageable);
}
