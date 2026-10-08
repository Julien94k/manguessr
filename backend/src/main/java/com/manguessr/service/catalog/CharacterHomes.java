package com.manguessr.service.catalog;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Oeuvre d'origine d'un personnage, et apparitions a ecarter du jeu.
 *
 * AniList rattache un personnage a toutes les oeuvres ou il apparait, cameos et crossovers
 * compris : Gyeong-Un Mok (heros de « Myst, Might, Mayhem ») figure en second role dans
 * « Nano Machine », Denji dans « Dandadan », Emilia dans « Isekai Quartet ». Tire depuis la
 * fiche de l'apparition, le portrait avait pour solution une oeuvre que personne n'associe
 * au personnage.
 *
 * Les saisons d'une meme serie ne sont pas concernees : seule une personne presente dans
 * plusieurs <b>series</b> a une origine a determiner. Elle est, dans l'ordre : la serie ou le
 * personnage est principal (MAIN), la plus ancienne (un crossover vient apres les oeuvres
 * qu'il reunit), la plus populaire. Toutes ses fiches des autres series sont des apparitions.
 *
 * Sans aucun role connu (catalogue anterieur a leur ingestion), rien n'est marque : l'annee
 * seule designerait « Nano Machine » comme origine de Gyeong-Un Mok.
 */
public final class CharacterHomes {

    /** Une fiche personnage, avec ce qu'il faut de son oeuvre pour en juger. */
    public record Appearance(long characterId, int personId, String role,
                             Integer seriesKey, Integer year, Integer popularity) {}

    private static final Comparator<Appearance> HOME_FIRST = Comparator
            .comparing((Appearance a) -> !"MAIN".equals(a.role()))
            .thenComparing(Appearance::year, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Appearance::popularity, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingLong(Appearance::characterId);

    private CharacterHomes() {}

    /** Identifiants des fiches qui sont des apparitions hors de l'oeuvre d'origine. */
    public static Set<Long> guests(Collection<Appearance> appearances) {
        Map<Integer, List<Appearance>> byPerson = appearances.stream()
                .filter(a -> a.seriesKey() != null)
                .collect(Collectors.groupingBy(Appearance::personId));

        Set<Long> guests = new HashSet<>();
        for (List<Appearance> person : byPerson.values()) {
            boolean severalSeries = person.stream().map(Appearance::seriesKey).distinct().count() > 1;
            boolean roleKnown = person.stream().anyMatch(a -> a.role() != null);
            if (!severalSeries || !roleKnown) {
                continue;
            }
            Integer home = person.stream().min(HOME_FIRST).orElseThrow().seriesKey();
            person.stream()
                    .filter(a -> !home.equals(a.seriesKey()))
                    .forEach(a -> guests.add(a.characterId()));
        }
        return guests;
    }
}
