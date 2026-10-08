import React, { useEffect, useState } from 'react';
import Card from '@/components/ui/Card';
import { formatPlayDate, formatPoints } from '@/components/game/gameUi';
import { fetchLeaderboard } from '@/services/api';
import { useAppStore } from '@/store/useAppStore';
import { Leaderboard, LeaderboardEntry, LeaderboardPeriod, Theme } from '@/types';

const PERIODS: { id: LeaderboardPeriod; label: string }[] = [
  { id: 'daily', label: "Aujourd'hui" },
  { id: 'weekly', label: 'Semaine' },
  { id: 'alltime', label: 'Tout temps' },
];

const UNIVERSES: { id: Theme | 'all'; label: string }[] = [
  { id: 'all', label: 'Tous' },
  { id: 'anime', label: 'Anime' },
  { id: 'manga', label: 'Manga' },
];

const Segmented = <T extends string>({
  options,
  value,
  onChange,
}: {
  options: { id: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) => (
  <div className="flex border-2 border-ink">
    {options.map((option) => (
      <button
        key={option.id}
        type="button"
        onClick={() => onChange(option.id)}
        className={`px-3 py-1 text-sm font-bold ${
          option.id === value ? 'bg-ink text-paper' : 'bg-sheet text-muted hover:text-ink'
        }`}
      >
        {option.label}
      </button>
    ))}
  </div>
);

/** Le podium sort du rang : son numero est imprime en gros, les suivants restent discrets. */
const Row: React.FC<{ entry: LeaderboardEntry; highlighted: boolean }> = ({ entry, highlighted }) => (
  <tr className={highlighted ? 'bg-accent/10' : ''}>
    <td className="px-4 py-2.5">
      <span className={entry.rank <= 3 ? 'font-display text-xl text-accent' : 'text-sm font-bold text-muted'}>
        {entry.rank}
      </span>
    </td>
    <td className={`px-4 py-2.5 text-sm ${highlighted ? 'font-bold' : 'font-medium'}`}>{entry.username}</td>
    <td className="px-4 py-2.5 text-right font-display tabular-nums">{formatPoints(entry.score)}</td>
    <td className="px-4 py-2.5 text-right text-sm text-muted">{entry.games}</td>
  </tr>
);

const LeaderboardPage: React.FC = () => {
  const username = useAppStore((state) => state.username);
  const [period, setPeriod] = useState<LeaderboardPeriod>('daily');
  const [universe, setUniverse] = useState<Theme | 'all'>('all');
  const [board, setBoard] = useState<Leaderboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    fetchLeaderboard(period, universe)
      .then((response) => {
        if (!cancelled) {
          setBoard(response);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Classement indisponible.');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [period, universe]);

  const meOutsideTop =
    board?.me && !board.entries.some((entry) => entry.username === board.me?.username) ? board.me : null;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-4xl">Classement</h1>
          {board && (
            <p className="kicker mt-1">
              {board.from === board.to
                ? `Défi du ${formatPlayDate(board.to)}`
                : period === 'weekly'
                  ? `Du ${formatPlayDate(board.from)} au ${formatPlayDate(board.to)}`
                  : 'Toutes les parties quotidiennes'}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <Segmented options={PERIODS} value={period} onChange={setPeriod} />
          <Segmented options={UNIVERSES} value={universe} onChange={setUniverse} />
        </div>
      </div>

      {error && <p className="text-center text-sm font-bold text-accent">{error}</p>}

      <Card className="overflow-hidden !p-0">
        {board && board.entries.length === 0 ? (
          <p className="screentone px-6 py-10 text-center text-sm text-muted">
            Aucun score sur cette période. Seules les parties quotidiennes de joueurs connectés comptent.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-ink text-left text-xs uppercase tracking-wider text-paper">
                <tr>
                  <th className="px-4 py-2 font-bold">#</th>
                  <th className="px-4 py-2 font-bold">Joueur</th>
                  <th className="px-4 py-2 text-right font-bold">Score</th>
                  <th className="px-4 py-2 text-right font-bold">Parties</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-dashed divide-ink/30">
                {board?.entries.map((entry) => (
                  <Row key={entry.username} entry={entry} highlighted={entry.username === username} />
                ))}
                {meOutsideTop && <Row entry={meOutsideTop} highlighted />}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};

export default LeaderboardPage;
