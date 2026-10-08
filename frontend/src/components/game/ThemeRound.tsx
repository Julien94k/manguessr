import React from 'react';
import AudioPlayer from '@/components/game/AudioPlayer';
import GuessForm from '@/components/game/GuessForm';
import ImageZoom from '@/components/game/ImageZoom';
import LockIcon from '@/components/ui/LockIcon';
import { formatPoints } from '@/components/game/gameUi';
import { RoundComponentProps } from '@/types';

/** Modes Opening et Ending : l'audio est offert, le clip video coute la moitie des points. */
const ThemeRound: React.FC<RoundComponentProps> = ({ round, theme, busy, suggest, onGuess, onClue, onSkip }) => {
  const audio = round.media.find((media) => media.kind === 'AUDIO');
  const video = round.media.find((media) => media.kind === 'VIDEO');
  const cover = round.media.find((media) => media.kind === 'IMAGE');
  const videoClue = round.clues.find((clue) => !clue.free);
  const isOpen = round.status === 'IN_PROGRESS';

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
        {cover && (
          <ImageZoom
            src={cover.url}
            alt="Jaquette floutée"
            className="h-40 w-28 flex-none -rotate-2 self-center border-2 border-ink object-cover shadow-hard"
          />
        )}
        <div className="w-full flex-1 space-y-4">
          {audio ? <AudioPlayer key={audio.url} src={audio.url} /> : <p className="text-sm text-muted">Générique indisponible.</p>}
          {isOpen && (
            <div className="flex items-baseline justify-between gap-3">
              <span className="kicker">En jeu</span>
              <span>
                <strong className="font-display text-3xl tabular-nums">{formatPoints(round.maxScore)}</strong>
                <span className="ml-1 text-sm text-muted">pts</span>
              </span>
            </div>
          )}
        </div>
      </div>

      {video ? (
        <video key={video.url} controls autoPlay src={video.url} className="aspect-video w-full animate-rise border-2 border-ink bg-black shadow-hard" />
      ) : (
        isOpen &&
        videoClue && (
          <button
            type="button"
            disabled={busy}
            onClick={onClue}
            className="screentone flex w-full items-center justify-center gap-3 border-2 border-dashed border-ink/60 py-5 text-sm font-bold text-muted hover:border-solid hover:border-accent hover:text-ink disabled:opacity-40"
          >
            <LockIcon className="h-4 w-4" />
            Pas d'idée ? Voir le clip vidéo
            <span className="text-accent">−{formatPoints(videoClue.cost)}</span>
          </button>
        )
      )}

      {isOpen && (
        <GuessForm
          theme={theme}
          suggest={suggest}
          busy={busy}
          onSkip={onSkip}
          placeholder="Titre de l’anime…"
          hint="Une seule réponse par manche."
          onSubmit={async (answer) => ({ clear: (await onGuess(answer)) !== null })}
        />
      )}
    </div>
  );
};

export default ThemeRound;
