package com.manguessr.service.game.mode;

import java.util.HashSet;
import java.util.Set;

/**
 * Ce qu'un tirage doit eviter, partage par toutes les manches d'une meme partie.
 *
 * Deux niveaux d'exigence :
 * <ul>
 *   <li><b>dans la partie</b> — une personne deja montree ne revient jamais : Ichigo appartient
 *       a onze oeuvres du catalogue (manga, one-shot, series, films), et le filtre limite a la
 *       manche le laissait revenir d'une manche a l'autre ;</li>
 *   <li><b>parties recentes</b> du joueur (parties libres, joueur connecte) — oeuvres et
 *       personnes evitees <i>si possible</i> : un vivier trop mince les rend de nouveau
 *       tirables plutot que de refuser la partie.</li>
 * </ul>
 * Le puzzle quotidien n'a pas de memoire recente, mais garde la regle intra-partie.
 */
public final class DraftMemory {

    private final Set<Long> recentWorks;
    private final Set<String> recentPeople;
    private final Set<String> peopleInGame = new HashSet<>();

    private DraftMemory(Set<Long> recentWorks, Set<String> recentPeople) {
        this.recentWorks = Set.copyOf(recentWorks);
        this.recentPeople = Set.copyOf(recentPeople);
    }

    public static DraftMemory empty() {
        return new DraftMemory(Set.of(), Set.of());
    }

    /**
     * @param works  oeuvres des parties recentes, portraits secondaires compris
     * @param people personnes deja montrees (voir {@link CharactersModeHandler#personKey})
     */
    public static DraftMemory recent(Set<Long> works, Set<String> people) {
        return new DraftMemory(works, people);
    }

    public Set<Long> recentWorks() {
        return recentWorks;
    }

    public boolean seenRecently(String person) {
        return recentPeople.contains(person);
    }

    public boolean shownInGame(String person) {
        return peopleInGame.contains(person);
    }

    public void markShown(String person) {
        peopleInGame.add(person);
    }
}
