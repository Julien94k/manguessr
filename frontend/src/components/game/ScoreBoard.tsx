import React from 'react';
import { DIFFICULTY_LABEL, formatPoints } from '@/components/game/gameUi';
import { GameSession, RoundStatus, Theme } from '@/types';

/** Verdict d'une manche, en un signe : on le lit sans la couleur. */
const STATUS_MARK: Record<RoundStatus, string> = {
  IN_PROGRESS: '',
  SOLVED: '○',
  FAILED: '×',
  SKIPPED: '—',
};

interface ScoreBoardProps {
  session: GameSession;
  theme: Theme;
  viewedOrdinal: number | null;
  onSelect: (ordinal: number | null) => void;
}

const TAB = 'flex items-center gap-2 border-2 border-ink px-3 py-1 text-sm font-bold';

/** Score total et navigation entre les manches, comme des onglets de chapitre. */
const ScoreBoard: React.FC<ScoreBoardProps> = ({ session, viewedOrdinal, onSelect }) => {
  const firstOpen = session.rounds.find((round) => round.status === 'IN_PROGRESS')?.ordinal;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      {session.rounds.length > 1 ? (
        <div className="flex flex-wrap gap-2">
          {session.rounds.map((round) => {
            // Les manches futures ne sont pas consultables : elles se jouent dans l'ordre.
            const reachable = round.status !== 'IN_PROGRESS' || round.ordinal === firstOpen;
            const isViewed = round.ordinal === viewedOrdinal;
            return (
              <button
                key={round.ordinal}
                type="button"
                disabled={!reachable}
                onClick={() => onSelect(round.ordinal)}
                className={`${TAB} disabled:cursor-not-allowed disabled:border-dashed disabled:opacity-40 ${
                  isViewed ? 'bg-ink text-paper' : 'bg-sheet hover:bg-paper'
                }`}
              >
                <span className="font-display">{round.ordinal + 1}</span>
                {DIFFICULTY_LABEL[round.difficulty]}
                {STATUS_MARK[round.status] && (
                  <span className={round.status === 'SOLVED' ? 'text-hit' : 'text-accent'}>
                    {STATUS_MARK[round.status]}
                  </span>
                )}
              </button>
            );
          })}
          {session.status === 'FINISHED' && (
            <button
              type="button"
              onClick={() => onSelect(null)}
              className={`${TAB} ${viewedOrdinal === null ? 'bg-ink text-paper' : 'bg-sheet hover:bg-paper'}`}
            >
              Bilan
            </button>
          )}
        </div>
      ) : (
        <span />
      )}

      <p className="text-sm">
        <span className="kicker mr-2">Score</span>
        <strong className="font-display text-2xl tabular-nums">{formatPoints(session.totalScore)}</strong>
        <span className="text-muted"> / {formatPoints(session.maxScore)}</span>
      </p>
    </div>
  );
};

export default ScoreBoard;
