import React, { useEffect, useRef } from 'react';
import { formatPoints } from '@/components/game/gameUi';
import { GameModeId, Round, Theme } from '@/types';

interface RoundResultProps {
  round: Round;
  mode: GameModeId;
  theme: Theme;
  hasNext: boolean;
  onContinue: () => void;
}

/** Le verdict, en onomatopee de case de manga. */
function headline(round: Round, mode: GameModeId): { text: string; tone: string } {
  if (mode === 'CHARACTERS') {
    return round.status === 'SKIPPED'
      ? { text: 'Passée…', tone: 'text-muted' }
      : { text: round.status === 'SOLVED' ? 'Sans faute !' : 'Terminé !', tone: 'text-hit' };
  }
  switch (round.status) {
    case 'SOLVED':
      return { text: 'Trouvé !', tone: 'text-hit' };
    case 'FAILED':
      return { text: 'Raté…', tone: 'text-accent' };
    default:
      return { text: 'Passée…', tone: 'text-muted' };
  }
}

/** Bilan d'une manche close : verdict, points, oeuvre revelee. */
const RoundResult: React.FC<RoundResultProps> = ({ round, mode, hasNext, onContinue }) => {
  const { text, tone } = headline(round, mode);
  const solution = round.solution;

  // Le mode Images donne a l'image toute la hauteur libre : le verdict arrive donc sous la
  // ligne de flottaison. On l'amene a l'ecran, sans bouger la page s'il y est deja.
  // Un seul appel au montage ne suffit pas : la cloture affiche la derniere image, qui se
  // charge ensuite et repousse le verdict de ~300 px. On le suit donc tant que la page
  // change de taille, pendant un court instant.
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const bringIntoView = () => ref.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    bringIntoView();
    const observer = new ResizeObserver(bringIntoView);
    observer.observe(document.body);
    const stop = window.setTimeout(() => observer.disconnect(), 1500);
    return () => {
      window.clearTimeout(stop);
      observer.disconnect();
    };
  }, []);

  // Entree enchaine : on joue une partie entiere au clavier. Ignore dans un champ (le Mangadle
  // garde son tableau a l'ecran) et en repetition : la touche maintenue qui a valide la
  // reponse ne doit pas sauter le verdict.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (e.key !== 'Enter' || e.repeat || target?.closest('input, textarea, button, a')) {
        return;
      }
      e.preventDefault();
      onContinue();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onContinue]);

  return (
    <div
      ref={ref}
      className="screentone flex flex-col gap-5 border-2 border-ink p-4 sm:flex-row sm:items-center"
    >
      {solution?.coverUrl && (
        <img
          src={solution.coverUrl}
          alt={solution.title}
          className="h-36 w-24 flex-none -rotate-2 self-center border-2 border-ink object-cover shadow-hard"
        />
      )}

      <div className="flex-1 space-y-1">
        <p className={`inline-block animate-stamp font-display text-3xl leading-none ${tone}`}>
          {text} <span className="text-ink">+{formatPoints(round.score)}</span>
        </p>
        {solution && (
          <div className="pt-1">
            <p className="text-lg font-bold">
              {solution.title}
              {solution.year && <span className="ml-2 text-sm font-medium text-muted">({solution.year})</span>}
            </p>
            {solution.altTitle && <p className="text-sm text-muted">{solution.altTitle}</p>}
            {solution.songTitle && (
              <p className="text-sm">
                ♪ {solution.songTitle}
                {solution.artist && ` — ${solution.artist}`}
              </p>
            )}
            {solution.externalUrl && (
              <a href={solution.externalUrl} target="_blank" rel="noreferrer" className="link-quiet">
                Voir sur AniList
              </a>
            )}
          </div>
        )}
      </div>

      <div className="flex flex-col items-center gap-1.5">
        <button type="button" onClick={onContinue} className="btn btn-accent">
          {hasNext ? 'Manche suivante →' : 'Voir le bilan'}
        </button>
        <span className="text-[0.7rem] text-muted">
          ou <kbd className="border border-ink/40 px-1 font-bold">Entrée ↵</kbd>
        </span>
      </div>
    </div>
  );
};

export default RoundResult;
