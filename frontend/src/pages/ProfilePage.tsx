import React, { useEffect, useState } from 'react';
import { fetchMe, fetchPlayerStats } from '@/services/api';
import { PlayerStats, UserProfile } from '@/types';
import Card from '@/components/ui/Card';
import { formatPlayDate, formatPoints } from '@/components/game/gameUi';

const Stat: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <Card className="!p-4">
    <p className="kicker">{label}</p>
    <p className="mt-1 font-display text-2xl tabular-nums">{value}</p>
  </Card>
);

const universeLabel = (theme: 'ANIME' | 'MANGA') => (theme === 'ANIME' ? 'Anime' : 'Manga');

const ProfilePage: React.FC = () => {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [stats, setStats] = useState<PlayerStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([fetchMe(), fetchPlayerStats()])
      .then(([me, playerStats]) => {
        setProfile(me);
        setStats(playerStats);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Profil indisponible.'));
  }, []);

  if (error) {
    return <p className="text-center text-sm font-bold text-accent">{error}</p>;
  }

  if (!profile || !stats) {
    return <p className="text-center text-sm text-muted">Chargement…</p>;
  }

  const days = (count: number) => `${count} jour${count > 1 ? 's' : ''}`;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-4xl">{profile.username}</h1>
        <p className="text-sm text-muted">
          {profile.email} · inscrit le {new Date(profile.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Série actuelle" value={days(stats.currentStreak)} />
        <Stat label="Meilleure série" value={days(stats.bestStreak)} />
        <Stat label="Parties" value={String(stats.gamesPlayed)} />
        <Stat label="Score cumulé" value={formatPoints(stats.totalScore)} />
      </div>

      {stats.gamesPlayed === 0 ? (
        <Card>
          <p className="text-center text-sm text-muted">
            Terminez un défi du jour pour démarrer votre série et vos statistiques.
          </p>
        </Card>
      ) : (
        <>
          <Card className="overflow-hidden !p-0">
            <h2 className="px-4 pt-4 font-display text-xl">Par mode</h2>
            <div className="overflow-x-auto">
              <table className="mt-2 w-full text-sm">
                <thead className="bg-ink text-left text-xs uppercase tracking-wider text-paper">
                  <tr>
                    <th className="px-4 py-2 font-bold">Mode</th>
                    <th className="px-4 py-2 text-right font-bold">Parties</th>
                    <th className="px-4 py-2 text-right font-bold">Moyenne</th>
                    <th className="px-4 py-2 text-right font-bold">Record</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-dashed divide-ink/30">
                  {stats.modes.map((mode) => (
                    <tr key={`${mode.theme}-${mode.mode}`}>
                      <td className="px-4 py-2.5">
                        {mode.title}
                        <span className="ml-2 text-xs text-muted">{universeLabel(mode.theme)}</span>
                      </td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{mode.played}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{formatPoints(mode.averageScore)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">
                        {formatPoints(mode.bestScore)}
                        <span className="text-muted"> / {formatPoints(mode.maxScore)}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card className="space-y-2">
            <h2 className="font-display text-xl">Dernières parties</h2>
            <ul className="divide-y divide-dashed divide-ink/30">
              {stats.history.map((entry) => (
                <li key={entry.sessionId} className="flex items-center justify-between py-2 text-sm">
                  <span>
                    <span className="text-muted">{formatPlayDate(entry.playDate)}</span>
                    {' · '}
                    {entry.title}
                    <span className="ml-2 text-xs text-muted">{universeLabel(entry.theme)}</span>
                  </span>
                  <span className="tabular-nums">
                    <strong>{formatPoints(entry.score)}</strong>
                    <span className="text-muted"> / {formatPoints(entry.maxScore)}</span>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
    </div>
  );
};

export default ProfilePage;
