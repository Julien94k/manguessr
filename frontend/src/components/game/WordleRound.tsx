import React from 'react';
import GuessForm from '@/components/game/GuessForm';
import ImageZoom from '@/components/game/ImageZoom';
import WordleTable from '@/components/game/WordleTable';
import { formatPoints } from '@/components/game/gameUi';
import LockIcon from '@/components/ui/LockIcon';
import { Clue as ClueInfo, RoundComponentProps } from '@/types';

/**
 * Seuils de deblocage des indices, en nombre d'essais.
 * Miroir de app.game.wordle.* : l'API envoie le contenu des indices, pas leurs seuils.
 */
const CLUE_THRESHOLDS = { cover: 12, synopsis: 18, character: 21 } as const;

/**
 * Un indice debloque.
 *
 * Auparavant les trois indices se partageaient trois colonnes egales, <b>verrouilles
 * compris</b> : la couverture n'avait droit qu'a une vignette de 90 px rognee, pendant que
 * deux tiers de la place servaient a afficher deux cadenas. Seuls les indices reellement
 * disponibles occupent desormais de la place, et ils se partagent une ligne — la couverture
 * a sa taille, le synopsis a cote plutot qu'en dessous, pour que le tableau de comparaison
 * reste a portee de regard.
 */
const Clue: React.FC<{ title: string; className?: string; children: React.ReactNode }> = ({
  title,
  className = '',
  children,
}) => (
  <div className={`panel p-3 ${className}`}>
    <p className="kicker">{title}</p>
    <div className="mt-2">{children}</div>
  </div>
);

/**
 * Indices encore verrouilles : une seule ligne d'etiquettes, pas trois colonnes vides.
 * Le serveur propose un saut jusqu'au prochain indice apres 3 essais ({@code skip}) : les
 * essais sautes comptent comme des erreurs, d'ou le cout affiche.
 */
const LockedClues: React.FC<{ labels: string[]; skip?: ClueInfo; busy: boolean; onSkip: () => void }> = ({
  labels,
  skip,
  busy,
  onSkip,
}) =>
  labels.length === 0 ? null : (
    <div className="flex flex-wrap items-center gap-2">
      {labels.map((label) => (
        <span
          key={label}
          className="inline-flex items-center gap-1.5 border-2 border-dashed border-ink/50 px-2.5 py-1 text-xs font-bold text-muted"
        >
          <LockIcon />
          {label}
        </span>
      ))}
      {skip && (
        <button type="button" disabled={busy} onClick={onSkip} className="btn btn-paper !px-3 !py-1 !text-xs">
          Sauter à l'indice {skip.label.toLowerCase()} <span className="text-accent">−{formatPoints(skip.cost)}</span>
        </button>
      )}
    </div>
  );

/** Modes Anidle et Mangadle : deduire l'oeuvre a partir des attributs partages. */
const WordleRound: React.FC<RoundComponentProps> = ({ round, theme, busy, suggest, onGuess, onClue, onSkip }) => {
  const isOpen = round.status === 'IN_PROGRESS';
  const cover = round.media.find((media) => media.kind === 'IMAGE');
  const attempts = round.attemptsUsed;
  const skip = round.clues[0];

  const skipToClue = () => {
    if (
      skip &&
      window.confirm(
        `Passer directement à l'indice ${skip.label.toLowerCase()} ? Les essais sautés comptent comme des erreurs (−${formatPoints(skip.cost)} pts).`
      )
    ) {
      onClue();
    }
  };

  const remaining = (threshold: number) => {
    const left = threshold - attempts;
    return `${left} essai${left > 1 ? 's' : ''}`;
  };

  const locked = [
    attempts < CLUE_THRESHOLDS.cover ? `Couverture dans ${remaining(CLUE_THRESHOLDS.cover)}` : null,
    attempts < CLUE_THRESHOLDS.synopsis ? `Synopsis dans ${remaining(CLUE_THRESHOLDS.synopsis)}` : null,
    attempts < CLUE_THRESHOLDS.character ? `Personnage dans ${remaining(CLUE_THRESHOLDS.character)}` : null,
  ].filter((label): label is string => label !== null);

  return (
    <div className="space-y-4">
      {isOpen && (
        <>
          <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm text-muted">
            <span>
              Essai <strong className="font-display text-lg text-ink">{round.attemptsUsed + 1}</strong> / {round.maxAttempts}
            </span>
            <span>
              Potentiel <strong className="font-display text-lg text-ink">{formatPoints(round.maxScore)}</strong> pts
              <span className="ml-1 text-xs">(−500 par erreur dès la 4ᵉ)</span>
            </span>
          </div>
          <GuessForm
            theme={theme}
            suggest={suggest}
            busy={busy}
            onSkip={onSkip}
            skipLabel="Abandonner"
            placeholder={theme === 'anime' ? 'Proposez un anime…' : 'Proposez un manga…'}
            onSubmit={async (answer) => {
              const result = await onGuess(answer);
              // Un doublon ou un titre inconnu est rejete : on laisse la saisie pour correction.
              const consumed = result !== null && result.round.attemptsUsed > round.attemptsUsed;
              return { clear: consumed, wrong: !result?.correct };
            }}
          />

          <LockedClues labels={locked} skip={skip} busy={busy} onSkip={skipToClue} />

          {attempts >= CLUE_THRESHOLDS.cover && (
            <div className="flex flex-wrap gap-4">
              {cover && (
                <Clue title="Couverture">
                  <ImageZoom src={cover.url} alt="Couverture floutée" className="h-56 w-auto object-contain sm:h-72" />
                </Clue>
              )}
              {attempts >= CLUE_THRESHOLDS.synopsis && (
                <Clue title="Synopsis" className="min-w-[18rem] flex-1">
                  <p className="max-h-60 overflow-y-auto text-sm">{round.synopsis ?? 'Pas de synopsis pour cette œuvre.'}</p>
                </Clue>
              )}
              {attempts >= CLUE_THRESHOLDS.character && (
                <Clue title="Personnage phare">
                  <p className="font-display text-lg">{round.topCharacter ?? '—'}</p>
                </Clue>
              )}
            </div>
          )}
        </>
      )}

      {round.wordleRows.length > 0 ? (
        <WordleTable rows={round.wordleRows} theme={theme} />
      ) : (
        <p className="screentone border-2 border-dashed border-ink/50 px-4 py-6 text-center text-sm">
          Proposez un premier titre : chaque essai révèle ce qu'il partage avec l'œuvre du jour.
          <br />
          <span className="bg-hit px-1 font-bold text-on-accent">vert</span> = commun, <span className="bg-near px-1 font-bold text-[#18140f]">jaune</span> =
          tag secondaire, ↑↓ = plus grand ou plus petit.
        </p>
      )}
    </div>
  );
};

export default WordleRound;
