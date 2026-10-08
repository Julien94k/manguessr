import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSession, skipRound, startSession, submitGuess, unlockClue } from '@/services/api';
import { useAppStore } from '@/store/useAppStore';
import { readStoredDaily, storeDaily } from '@/hooks/useDailyProgress';
import { CharacterGuess, GameModeId, GameSession, GuessResult, Round, Theme } from '@/types';

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback;
}

/**
 * Cycle de vie d'une partie : demarrage ou reprise, tentatives, indices, abandon.
 *
 * Le hook ne calcule rien : chaque action renvoie l'etat autoritatif du serveur, qui
 * remplace l'etat local. Le score affiche ne peut donc jamais diverger du score enregistre.
 *
 * @param mode nul tant que le mode n'est pas valide pour l'univers : rien n'est demarre
 */
export function useGameSession(mode: GameModeId | null, theme: Theme, unlimited: boolean) {
  const isLoggedIn = useAppStore((state) => state.token !== null);

  const [session, setSession] = useState<GameSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  // Ignore les reponses d'un chargement devenu obsolete (changement d'univers en cours).
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const current = ++requestId.current;
    setSession(null);
    setError(null);
    setNotice(null);

    if (!mode) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);

    try {
      const anonymousDaily = !unlimited && !isLoggedIn;
      const storedId = anonymousDaily ? (readStoredDaily(theme, mode)?.id ?? null) : null;

      let loaded: GameSession | null =
        storedId === null ? null : await fetchSession(storedId).catch(() => null);

      if (!loaded) {
        loaded = await startSession({ mode, theme, unlimited });
      }

      if (current === requestId.current) {
        setSession(loaded);
      }
    } catch (err: unknown) {
      if (current === requestId.current) {
        setError(errorMessage(err, 'Impossible de démarrer la partie.'));
      }
    } finally {
      if (current === requestId.current) {
        setIsLoading(false);
      }
    }
  }, [mode, theme, unlimited, isLoggedIn]);

  useEffect(() => {
    void load();
  }, [load]);

  // Memoire d'un anonyme, tenue a jour a chaque action : le defi du jour en tire son total.
  useEffect(() => {
    if (session && !session.unlimited && !isLoggedIn) {
      storeDaily(theme, session);
    }
  }, [session, theme, isLoggedIn]);

  const sessionId = session?.id ?? null;

  const perform = useCallback(
    async <T>(action: (id: number) => Promise<T>): Promise<T | null> => {
      if (sessionId === null) {
        return null;
      }
      setIsBusy(true);
      setNotice(null);
      try {
        return await action(sessionId);
      } catch (err: unknown) {
        setNotice(errorMessage(err, 'Action impossible.'));
        return null;
      } finally {
        setIsBusy(false);
      }
    },
    [sessionId]
  );

  const applyGuess = useCallback((result: GuessResult | null) => {
    if (result) {
      setSession(result.session);
      // Seul un essai rejete (doublon, titre inconnu) merite un avertissement : une manche
      // close affiche deja son bilan.
      if (!result.correct && result.message && result.round.status === 'IN_PROGRESS') {
        setNotice(result.message);
      }
    }
    return result;
  }, []);

  const guess = useCallback(
    async (roundOrdinal: number, answer: string) =>
      applyGuess(await perform((id) => submitGuess(id, { roundOrdinal, answer }))),
    [perform, applyGuess]
  );

  const guessCharacters = useCallback(
    async (roundOrdinal: number, entries: CharacterGuess[]) =>
      applyGuess(await perform((id) => submitGuess(id, { roundOrdinal, entries }))),
    [perform, applyGuess]
  );

  const clue = useCallback(
    async (roundOrdinal: number) => {
      const round: Round | null = await perform((id) => unlockClue(id, roundOrdinal));
      if (round) {
        setSession((previous) =>
          previous && {
            ...previous,
            rounds: previous.rounds.map((candidate) =>
              candidate.ordinal === round.ordinal ? round : candidate
            ),
          }
        );
      }
    },
    [perform]
  );

  const skip = useCallback(
    async (roundOrdinal: number) => {
      const updated = await perform((id) => skipRound(id, roundOrdinal));
      if (updated) {
        setSession(updated);
      }
    },
    [perform]
  );

  return {
    session,
    isLoading,
    error,
    notice,
    isBusy,
    isLoggedIn,
    restart: load,
    guess,
    guessCharacters,
    clue,
    skip,
  };
}
