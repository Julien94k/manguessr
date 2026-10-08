import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import GuessForm from '@/components/game/GuessForm';
import ImageZoom from '@/components/game/ImageZoom';
import LockIcon from '@/components/ui/LockIcon';
import { formatPoints } from '@/components/game/gameUi';
import { Clue, MediaRef, RoundComponentProps, Theme } from '@/types';

/** Trois images par manche, comme AniGuessr. */
const IMAGE_SLOTS = 3;

/** Marge sous l'image : de quoi laisser deviner qu'il y a quelque chose en dessous. */
const BOTTOM_MARGIN = 24;

/** Hauteur plancher : en deca, l'image n'est plus jouable, mieux vaut faire defiler. */
const MIN_HEIGHT = 260;

/** En dessous, la mise en page est sur une seule colonne et l'image suit une fraction de l'ecran. */
const TWO_COLUMN_BREAKPOINT = 1024;

/**
 * Hauteur maximale de l'image : exactement la place libre sous elle.
 *
 * Une fraction de `vh` ne peut pas convenir, car ce qui la precede varie — le defi du jour
 * ajoute son bandeau de progression, qui descend l'image de 144 px. Avec une valeur fixe,
 * l'image debordait alors sous la ligne de flottaison, et on en voyait <b>moins</b> : pour en
 * voir le bas il fallait faire defiler, donc perdre le champ de reponse de vue — le
 * va-et-vient qu'on cherche justement a eviter.
 */
function useAvailableHeight(dependency: unknown) {
  const frameRef = useRef<HTMLDivElement>(null);
  const [maxHeight, setMaxHeight] = useState<number | null>(null);

  const measure = useCallback(() => {
    const frame = frameRef.current;
    if (!frame || window.innerWidth < TWO_COLUMN_BREAKPOINT) {
      setMaxHeight(null);
      return;
    }
    // Position dans le document, et non dans la fenetre : le calcul ne doit pas dependre
    // de l'endroit ou le joueur a fait defiler la page.
    const top = frame.getBoundingClientRect().top + window.scrollY;
    setMaxHeight(Math.max(MIN_HEIGHT, window.innerHeight - top - BOTTOM_MARGIN));
  }, []);

  useLayoutEffect(() => {
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure, dependency]);

  return { frameRef, maxHeight };
}

interface StripProps {
  images: MediaRef[];
  clues: Clue[];
  selected: number;
  theme: Theme;
  isOpen: boolean;
  busy: boolean;
  className?: string;
  onSelect: (index: number) => void;
  onUnlock: () => void;
}

/**
 * Les indices et les images ne font qu'un : chaque case est une image deja obtenue (clic pour
 * l'afficher) ou l'indice suivant a debloquer (clic pour le payer), la derniere case etant
 * les initiales du titre. Auparavant, une rangee de boutons d'indice et une rangee de
 * vignettes disaient deux fois la meme chose, a deux endroits.
 */
const ClueStrip: React.FC<StripProps> = ({
  images,
  clues,
  selected,
  theme,
  isOpen,
  busy,
  className = '',
  onSelect,
  onUnlock,
}) => {
  // Cases fluides, au format du media : pages de manga en portrait, episodes en paysage.
  const size = theme === 'manga' ? 'aspect-[3/4] w-full' : 'aspect-[4/3] w-full';
  const nextLocked = clues.find((clue) => !clue.unlocked);

  return (
    <div className={`grid grid-cols-4 gap-2 ${className}`}>
      {clues.map((clue, index) => {
        const image = index < IMAGE_SLOTS ? images[index] : undefined;
        const base = `${size} relative flex flex-none flex-col items-center justify-center overflow-hidden border-2 text-center`;

        if (image) {
          return (
            <button
              key={clue.index}
              type="button"
              onClick={() => onSelect(index)}
              aria-label={`Afficher l'image ${index + 1}`}
              className={`${base} bg-ink ${
                index === selected ? 'border-accent shadow-hard-sm' : 'border-ink opacity-60 hover:opacity-100'
              }`}
            >
              <img src={image.url} alt="" className="h-full w-full object-cover" />
            </button>
          );
        }

        if (clue.unlocked) {
          // Initiales obtenues : elles s'affichent au-dessus du champ, la case le rappelle.
          return (
            <div key={clue.index} className={`${base} border-ink bg-ink text-paper`}>
              <span className="font-display text-xl leading-none">Aa</span>
              <span className="mt-1 text-[0.6rem] font-bold uppercase">Initiales</span>
            </div>
          );
        }

        const actionable = isOpen && clue === nextLocked;
        return (
          <button
            key={clue.index}
            type="button"
            disabled={!actionable || busy}
            onClick={onUnlock}
            title={actionable ? `Débloquer : ${clue.label} (−${formatPoints(clue.cost)} pts)` : undefined}
            className={`${base} screentone border-dashed border-ink/60 text-muted ${
              actionable
                ? 'hover:border-solid hover:border-accent hover:bg-accent/10 hover:text-ink'
                : 'cursor-not-allowed opacity-40'
            }`}
          >
            {index < IMAGE_SLOTS ? (
              <LockIcon className="h-4 w-4" />
            ) : (
              <span className="font-display text-lg leading-none">Aa</span>
            )}
            <span className="mt-1 text-[0.6rem] font-bold uppercase leading-tight">
              {index < IMAGE_SLOTS ? `Image ${index + 1}` : 'Initiales'}
            </span>
            {actionable && <span className="text-[0.65rem] font-bold text-accent">−{formatPoints(clue.cost)}</span>}
          </button>
        );
      })}
    </div>
  );
};

/**
 * Mode Images : une image offerte, deux a debloquer, puis les initiales du titre.
 *
 * Disposition en deux colonnes des `lg` — l'image a gauche, tout ce sur quoi on agit a
 * droite — comme le mode Cover-deblur. En une seule colonne, l'image et ses vignettes en
 * pleine largeur repoussaient le champ de reponse hors de l'ecran : il fallait faire
 * l'aller-retour entre l'image et la saisie a chaque essai.
 *
 * La hauteur de l'image est la place libre sous elle (`useAvailableHeight`). Une page de manga
 * est en portrait, donc limitee par la hauteur : les cases d'indices passent a droite sur grand
 * ecran pour lui laisser toute la colonne, et restent sous l'image sur petit ecran. Un clic sur
 * l'image l'agrandit.
 */
const ImageRound: React.FC<RoundComponentProps> = ({ round, theme, busy, suggest, onGuess, onClue, onSkip }) => {
  const images = round.media.filter((media) => media.kind === 'IMAGE');
  const [selected, setSelected] = useState(0);
  const isOpen = round.status === 'IN_PROGRESS';

  // Un indice qui ajoute une image la met au premier plan.
  useEffect(() => {
    setSelected(Math.max(0, images.length - 1));
  }, [images.length]);

  const current = images[Math.min(selected, images.length - 1)];
  const strip = {
    images,
    clues: round.clues,
    selected,
    theme,
    isOpen,
    busy,
    onSelect: setSelected,
    onUnlock: onClue,
  };
  // Re-mesure a chaque manche : le bandeau du defi du jour peut arriver apres coup.
  const { frameRef, maxHeight } = useAvailableHeight(round.ordinal);

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,21rem)] lg:items-start">
      <div className="space-y-3">
        <div
          ref={frameRef}
          className="flex min-h-[14rem] items-center justify-center overflow-hidden border-2 border-ink bg-[#14110e] shadow-hard"
        >
          {current && (
            <ImageZoom
              key={current.url}
              src={current.url}
              alt="Image à identifier"
              // w-full plutot que w-auto : les vignettes d'episodes d'AniList descendent parfois
              // sous 512 px de large, et s'afficheraient alors en timbre-poste au milieu de la
              // colonne. La largeur est bornee pres de la taille de rendu, pour ne pas les etirer.
              className="w-full max-w-2xl animate-rise object-contain max-lg:max-h-[52vh]"
              style={maxHeight === null ? undefined : { maxHeight }}
            />
          )}
        </div>
        <ClueStrip {...strip} className="mx-auto max-w-sm lg:hidden" />
      </div>

      <div className="space-y-4">
        {isOpen && (
          <div className="flex items-baseline justify-between gap-3">
            <span className="kicker">En jeu</span>
            <span>
              <strong className="font-display text-3xl tabular-nums">{formatPoints(round.maxScore)}</strong>
              <span className="ml-1 text-sm text-muted">pts</span>
            </span>
          </div>
        )}

        {round.titleHint && (
          <p className="animate-rise border-2 border-ink bg-sheet px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.3em]">
            {round.titleHint}
          </p>
        )}

        {isOpen && (
          <GuessForm
            theme={theme}
            suggest={suggest}
            busy={busy}
            onSkip={onSkip}
            hint="Une seule réponse : débloquez des indices avant si vous hésitez."
            onSubmit={async (answer) => ({ clear: (await onGuess(answer)) !== null })}
          />
        )}

        <div className="hidden lg:block">
          <p className="kicker mb-2">Indices</p>
          <ClueStrip {...strip} />
        </div>
      </div>
    </div>
  );
};

export default ImageRound;
