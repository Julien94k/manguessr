import { useCallback, useEffect, useState } from 'react';
import { fetchDaily } from '@/services/api';
import { useAppStore } from '@/store/useAppStore';
import { todayUtc } from '@/components/game/gameUi';
import { DailyModeState, DailyOverview, GameModeId, GameSession, Theme } from '@/types';

const storageKey = (theme: Theme, mode: GameModeId) => `manguessr-daily-${theme}-${mode}`;

interface StoredDaily {
  id: number;
  playDate: string;
  /** Absents des enregistrements anterieurs au defi enchaine : la partie compte alors comme entamee. */
  status: DailyModeState;
  score: number;
}

/**
 * Partie quotidienne d'un joueur anonyme, memorisee dans le navigateur.
 *
 * Pour un joueur connecte, le serveur retrouve seul la partie du jour et sa progression. Un
 * anonyme n'a pas d'identite cote serveur : sans cette memoire, recharger la page offrirait
 * une nouvelle partie du meme puzzle, et le defi du jour ne pourrait pas afficher son total.
 */
export function readStoredDaily(theme: Theme, mode: GameModeId, playDate = todayUtc()): StoredDaily | null {
  try {
    const raw = localStorage.getItem(storageKey(theme, mode));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<Record<keyof StoredDaily, unknown>>;
    if (typeof parsed.id !== 'number' || parsed.playDate !== playDate) {
      return null;
    }
    return {
      id: parsed.id,
      playDate,
      status: parsed.status === 'FINISHED' ? 'FINISHED' : 'IN_PROGRESS',
      score: typeof parsed.score === 'number' ? parsed.score : 0,
    };
  } catch {
    return null;
  }
}

export function storeDaily(theme: Theme, session: GameSession) {
  const entry: StoredDaily = {
    id: session.id,
    playDate: session.playDate,
    status: session.status,
    score: session.totalScore,
  };
  try {
    localStorage.setItem(storageKey(theme, session.mode), JSON.stringify(entry));
  } catch {
    // Stockage indisponible (navigation privee) : la partie reste jouable, sans reprise.
  }
}

/** Complete le defi du jour d'un anonyme avec les parties memorisees dans le navigateur. */
function withStoredProgress(overview: DailyOverview, theme: Theme): DailyOverview {
  const modes = overview.modes.map((entry) => {
    const stored = readStoredDaily(theme, entry.mode, overview.date);
    return stored ? { ...entry, status: stored.status, score: stored.score, sessionId: stored.id } : entry;
  });
  return { ...overview, modes, totalScore: modes.reduce((total, entry) => total + entry.score, 0) };
}

/**
 * Defi du jour d'un univers : progression par mode et score total.
 *
 * @param refreshKey change quand une partie avance, pour relire le total sans recharger la page
 */
export function useDailyProgress(theme: Theme, refreshKey?: string) {
  const isLoggedIn = useAppStore((state) => state.token !== null);
  const [overview, setOverview] = useState<DailyOverview | null>(null);

  const reload = useCallback(() => {
    fetchDaily(theme)
      .then((response) => setOverview(isLoggedIn ? response : withStoredProgress(response, theme)))
      // Le defi du jour enrichit les pages sans les conditionner : les modes restent jouables.
      .catch(() => setOverview(null));
  }, [theme, isLoggedIn]);

  useEffect(() => {
    reload();
  }, [reload, refreshKey]);

  return { overview, reload };
}
