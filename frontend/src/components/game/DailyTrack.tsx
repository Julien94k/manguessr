import React from 'react';
import { Link } from 'react-router-dom';
import { MODE_MARK, formatPoints, modeToSlug } from '@/components/game/gameUi';
import { DailyMode, DailyOverview, GameModeInfo, Theme } from '@/types';

interface DailyTrackProps {
  overview: DailyOverview;
  modes: GameModeInfo[];
  theme: Theme;
  /** Mode en cours de jeu, mis en avant ; nul sur l'accueil. */
  current: DailyMode['mode'] | null;
}

const STATUS_STYLE: Record<DailyMode['status'], string> = {
  NOT_STARTED: 'bg-sheet text-muted',
  IN_PROGRESS: 'bg-sheet text-ink border-dashed',
  FINISHED: 'bg-ink text-paper',
};

/**
 * Le defi du jour comme un parcours : score total de l'univers et etapes cliquables.
 *
 * Comme sur AniGuessr, le score du jour est la somme des modes : le bandeau le garde sous les
 * yeux pendant la partie, et permet de passer d'un mode a l'autre sans revenir a l'accueil.
 * Chaque etape porte le repere de son mode ; une etape jouee est imprimee en negatif.
 */
const DailyTrack: React.FC<DailyTrackProps> = ({ overview, modes, current }) => {
  const playable = new Map(modes.filter((mode) => mode.playable).map((mode) => [mode.id, mode]));
  const steps = overview.modes.filter((entry) => playable.has(entry.mode));
  const finished = steps.filter((entry) => entry.status === 'FINISHED').length;
  const maxScore = steps.reduce((total, entry) => total + entry.maxScore, 0);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-y-2 border-ink py-3">
      <ol className="flex flex-wrap gap-2">
        {steps.map((entry) => {
          const isCurrent = entry.mode === current;
          const title = playable.get(entry.mode)?.title ?? entry.title;
          return (
            <li key={entry.mode}>
              <Link
                to={`/play/${modeToSlug(entry.mode)}`}
                aria-current={isCurrent ? 'step' : undefined}
                title={entry.status === 'FINISHED' ? `${title} · ${formatPoints(entry.score)} pts` : title}
                className={`flex items-center gap-2 border-2 border-ink px-2.5 py-1 text-sm font-bold ${
                  isCurrent ? 'bg-accent text-on-accent shadow-hard-sm' : STATUS_STYLE[entry.status]
                }`}
              >
                <span className="font-display">{MODE_MARK[entry.mode]}</span>
                <span className="hidden sm:inline">{title}</span>
                {entry.status === 'FINISHED' && !isCurrent && (
                  <span className="text-xs font-medium tabular-nums opacity-80">{formatPoints(entry.score)}</span>
                )}
              </Link>
            </li>
          );
        })}
      </ol>
      <p className="text-sm">
        <span className="kicker mr-2">
          Défi du jour · {finished}/{steps.length}
        </span>
        <strong className="font-display text-lg tabular-nums text-accent">{formatPoints(overview.totalScore)}</strong>
        <span className="text-muted"> / {formatPoints(maxScore)}</span>
      </p>
    </div>
  );
};

export default DailyTrack;
