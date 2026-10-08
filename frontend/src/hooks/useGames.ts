import { useEffect, useState } from 'react';
import { fetchGames } from '@/services/api';
import { GameModeInfo, Theme } from '@/types';

/** Modes d'un univers, tels que le backend les decrit (titres, baremes, jouabilite). */
export function useGames(theme: Theme) {
  const [modes, setModes] = useState<GameModeInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchGames(theme)
      .then((response) => {
        if (!cancelled) {
          setModes(response);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setModes([]);
          setError(err instanceof Error ? err.message : 'Modes indisponibles.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [theme]);

  return { modes, isLoading, error };
}
