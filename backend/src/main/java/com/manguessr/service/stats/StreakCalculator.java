package com.manguessr.service.stats;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Series de jours joues consecutifs.
 *
 * Regle : la serie en cours reste active tant que la journee n'est pas finie. Un joueur qui a
 * joue hier mais pas encore aujourd'hui garde sa serie — la lui retirer a minuit, alors qu'il
 * peut encore jouer, serait punitif.
 */
public final class StreakCalculator {

    private StreakCalculator() {}

    /** Serie en cours, a la date du jour. */
    public static int current(Collection<LocalDate> playedDays, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(playedDays);
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);

        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Plus longue serie jamais atteinte. */
    public static int best(Collection<LocalDate> playedDays) {
        List<LocalDate> sorted = playedDays.stream().distinct().sorted().toList();

        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : sorted) {
            run = previous != null && day.equals(previous.plusDays(1)) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }
        return best;
    }
}
