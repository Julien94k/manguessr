package com.manguessr.service.game.mode;

import com.manguessr.model.dto.GuessRequest;
import com.manguessr.model.dto.RoundView;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.GameMode;
import com.manguessr.model.enums.WorkType;
import com.manguessr.service.media.MediaToken;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comportement propre a un mode de jeu.
 *
 * Chaque implementation est responsable de trois choses et d'elles seules : quelles oeuvres
 * peuvent etre tirees, ce que le joueur voit, et comment une reponse est jugee. Tout le reste
 * — persistance, transitions d'etat, total de la partie — est mutualise dans GameSessionService.
 */
public interface GameModeHandler {

    GameMode mode();

    /**
     * Identifiants des oeuvres jouables dans ce mode, pour un univers et un palier donnes.
     *
     * Le filtrage est indispensable : un tiers des animes n'a pas trois vignettes d'episodes,
     * et tirer une oeuvre sans la donnee requise produirait une manche injouable.
     */
    List<Long> eligibleWorkIds(WorkType type, Difficulty tier);

    /** Tire les elements de la manche et renvoie le contenu a figer. */
    RoundPayload prepare(MediaWork work, long seed);

    /**
     * Variante tenant compte de ce que la partie et le joueur ont deja vu. Seuls les modes
     * qui tirent autre chose que l'oeuvre ancre (Personnages) ont besoin de la surcharger.
     */
    default RoundPayload prepare(MediaWork work, long seed, DraftMemory memory) {
        return prepare(work, seed);
    }

    /**
     * Ce que des manches recentes d'un joueur apprennent a un nouveau tirage. Par defaut,
     * leurs oeuvres ancres.
     */
    default DraftMemory recall(List<SessionRound> recentRounds) {
        Set<Long> works = new HashSet<>();
        for (SessionRound round : recentRounds) {
            works.add(round.getWork().getId());
        }
        return DraftMemory.recent(works, Set.of());
    }

    /**
     * Construit la vue destinee au client.
     *
     * Ne doit jamais exposer l'oeuvre tant que la manche est ouverte.
     */
    RoundView toView(SessionRound round);

    /** Juge une tentative et met a jour l'etat de la manche. */
    GuessOutcome evaluate(SessionRound round, GuessRequest request);

    /**
     * Debloque l'indice suivant.
     *
     * @throws IllegalStateException si le mode n'a pas d'indice payant ou s'ils sont epuises
     */
    default void unlockClue(SessionRound round) {
        throw new IllegalStateException("Ce mode ne propose pas d'indice supplementaire.");
    }

    /**
     * Medias a rendre a l'avance, des la creation de la partie.
     *
     * Y compris ceux que le joueur ne voit pas encore : le but est justement que l'image du
     * prochain indice soit deja prete quand il le debloque. Renvoyer une liste vide desactive
     * le prechauffage pour ce mode — c'est le cas des generiques, relayes en flux sans cache.
     *
     * Prend l'oeuvre et le contenu serialise plutot qu'une manche : le puzzle quotidien est
     * prechauffe a sa generation, quand aucune partie n'existe encore.
     */
    default List<MediaToken> mediaToWarm(MediaWork work, String payload) {
        return List.of();
    }

    /** Commodite pour une manche de partie. */
    default List<MediaToken> mediaToWarm(SessionRound round) {
        return mediaToWarm(round.getWork(), round.getPayload());
    }
}
