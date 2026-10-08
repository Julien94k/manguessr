import React, { useState } from 'react';
import GuessForm from '@/components/game/GuessForm';
import ImageZoom from '@/components/game/ImageZoom';
import { formatPoints } from '@/components/game/gameUi';
import { RoundComponentProps } from '@/types';

/**
 * Mode Cover-deblur : la jaquette se defloute d'un cran a chaque essai rate.
 *
 * L'image recue est deja floutee par le serveur ; il n'y a volontairement aucun
 * `filter: blur()` ici, qu'un joueur pourrait retirer dans les outils de developpement.
 */
const CoverRevealRound: React.FC<RoundComponentProps> = ({
  round,
  theme,
  busy,
  suggest,
  onGuess,
  onSkip,
}) => {
  // Les reponses brutes ne sont pas renvoyees par l'API : l'historique n'existe que le temps de la page.
  const [misses, setMisses] = useState<string[]>([]);
  const cover = round.media.find((media) => media.kind === 'IMAGE');
  const isOpen = round.status === 'IN_PROGRESS';

  const submit = async (answer: string) => {
    const result = await onGuess(answer);
    const consumed = result !== null && result.round.attemptsUsed > round.attemptsUsed;
    if (consumed && !result.correct) {
      setMisses((previous) => [answer, ...previous]);
    }
    return { clear: consumed, wrong: !result?.correct };
  };

  return (
    <div className="grid gap-6 md:grid-cols-[minmax(0,20rem)_1fr]">
      <div className="mx-auto w-full max-w-xs">
        <div className="aspect-[2/3] overflow-hidden border-2 border-ink bg-ink shadow-hard-lg">
          {cover && (
            <ImageZoom key={cover.url} src={cover.url} alt="Couverture à identifier" className="h-full w-full animate-rise object-cover" />
          )}
        </div>
      </div>

      <div className="space-y-4">
        <div>
          <div className="flex items-baseline justify-between gap-3">
            <span className="kicker">
              Essai {Math.min(round.attemptsUsed + 1, round.maxAttempts)} / {round.maxAttempts} · l'image se précise à chaque erreur
            </span>
            {isOpen && (
              <span className="flex-none">
                <strong className="font-display text-3xl tabular-nums">{formatPoints(round.maxScore)}</strong>
                <span className="ml-1 text-sm text-muted">pts</span>
              </span>
            )}
          </div>
          <div className="mt-2 flex gap-1">
            {Array.from({ length: round.maxAttempts }, (_, index) => (
              <span
                key={index}
                className={`h-3 flex-1 border-2 border-ink transition-colors ${index < round.attemptsUsed ? 'bg-accent' : 'bg-sheet'}`}
              />
            ))}
          </div>
        </div>

        {isOpen && (
          <GuessForm
            theme={theme}
            suggest={suggest}
            busy={busy}
            onSkip={onSkip}
            skipLabel="Abandonner"
            placeholder="Titre du manga…"
            onSubmit={submit}
          />
        )}

        {misses.length > 0 && (
          <ul className="flex flex-wrap gap-2" aria-label="Réponses déjà tentées">
            {misses.map((miss, index) => (
              <li
                key={`${miss}-${index}`}
                className="animate-rise border-2 border-ink/40 px-2 py-1 text-sm text-muted line-through decoration-accent decoration-2"
              >
                {miss}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default CoverRevealRound;
