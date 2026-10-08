import React, { createRef, useMemo, useRef, useState } from 'react';
import AnswerAutocomplete from '@/components/game/AnswerAutocomplete';
import ImageZoom from '@/components/game/ImageZoom';
import { useAutocomplete } from '@/hooks/useAutocomplete';
import { CharacterGuess, Round, Theme } from '@/types';

interface CharactersRoundProps {
  round: Round;
  theme: Theme;
  busy: boolean;
  titleSuggest: (input: string) => string[];
  onSubmit: (entries: CharacterGuess[]) => void;
  onSkip: () => void;
}

const Verdict: React.FC<{ found: boolean; label: string; value: string | null }> = ({ found, label, value }) => (
  <p className={`text-sm ${found ? 'text-hit' : 'text-accent'}`}>
    <span className="mr-1 font-bold">{found ? '○' : '×'}</span>
    <span className="kicker">{label} </span>
    <span className="font-medium">{value ?? '—'}</span>
  </p>
);

/**
 * Mode Personnages : quatre portraits d'oeuvres differentes, nom et titre a trouver.
 * Les huit reponses partent en une seule validation, comme sur AniGuessr.
 */
const CharactersRound: React.FC<CharactersRoundProps> = ({ round, theme, busy, titleSuggest, onSubmit, onSkip }) => {
  const { suggest: characterSuggest } = useAutocomplete(theme, 'character');
  const [entries, setEntries] = useState<CharacterGuess[]>(() =>
    round.characters.map(() => ({ character: '', title: '' }))
  );
  const isOpen = round.status === 'IN_PROGRESS';

  const update = (slot: number, field: keyof CharacterGuess, value: string) => {
    setEntries((previous) => previous.map((entry, index) => (index === slot ? { ...entry, [field]: value } : entry)));
  };

  const filled = entries.some((entry) => entry.character.trim() || entry.title.trim());
  const answered = entries.reduce(
    (count, entry) => count + (entry.character.trim() ? 1 : 0) + (entry.title.trim() ? 1 : 0),
    0
  );

  // Huit champs dans l'ordre de lecture (nom puis oeuvre, portrait par portrait) : Entree passe
  // au suivant, et le dernier mene au bouton de validation. Valider d'un Entree aurait ete
  // trop facile a faire par megarde : la validation est unique et ferme la manche.
  const fieldRefs = useMemo(
    () => round.characters.flatMap(() => [createRef<HTMLInputElement>(), createRef<HTMLInputElement>()]),
    [round.characters]
  );
  const submitRef = useRef<HTMLButtonElement>(null);
  const focusAfter = (position: number) => {
    const next = fieldRefs[position + 1]?.current;
    if (next) {
      next.focus();
    } else {
      submitRef.current?.focus();
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {round.characters.map((slot) => (
          // Pas d'overflow-hidden sur la carte : il coupait la liste de suggestions des champs,
          // qui deborde du cadre. L'arrondi est porte par la seule zone de l'image.
          <div
            key={slot.slot}
            className="panel"
          >
            <div className="relative aspect-[3/4] overflow-hidden border-b-2 border-ink bg-ink">
              <ImageZoom src={slot.imageUrl} alt={`Personnage ${slot.slot + 1}`} className="h-full w-full object-cover" />
              <span className="pointer-events-none absolute left-0 top-0 border-b-2 border-r-2 border-ink bg-paper px-2 font-display text-lg">
                {slot.slot + 1}
              </span>
            </div>
            <div className="space-y-2 p-3">
              {isOpen ? (
                <>
                  <AnswerAutocomplete
                    value={entries[slot.slot]?.character ?? ''}
                    onChange={(value) => update(slot.slot, 'character', value)}
                    suggest={characterSuggest}
                    placeholder="Personnage…"
                    disabled={busy}
                    inputRef={fieldRefs[slot.slot * 2]}
                    submitEmpty
                    onSubmit={() => focusAfter(slot.slot * 2)}
                    autoFocus={slot.slot === 0}
                  />
                  <AnswerAutocomplete
                    value={entries[slot.slot]?.title ?? ''}
                    onChange={(value) => update(slot.slot, 'title', value)}
                    suggest={titleSuggest}
                    placeholder="Œuvre…"
                    disabled={busy}
                    inputRef={fieldRefs[slot.slot * 2 + 1]}
                    submitEmpty
                    onSubmit={() => focusAfter(slot.slot * 2 + 1)}
                  />
                </>
              ) : (
                <>
                  <Verdict found={slot.nameFound} label="Nom" value={slot.name} />
                  <Verdict found={slot.titleFound} label="Œuvre" value={slot.workTitle} />
                </>
              )}
            </div>
          </div>
        ))}
      </div>

      {isOpen && (
        <div className="flex flex-col items-center justify-between gap-3 sm:flex-row">
          <p className="text-xs text-muted">
            <strong className="text-ink">{answered} / {fieldRefs.length}</strong> réponses · 2 000 pts par personnage,
            500 par œuvre. Entrée passe au champ suivant ; laissez vide ce que vous ne savez pas.
          </p>
          <div className="flex items-center gap-4">
            <button type="button" disabled={busy} onClick={onSkip} className="link-quiet">
              Passer
            </button>
            <button
              type="button"
              disabled={busy || !filled}
              onClick={() => onSubmit(entries)}
              ref={submitRef}
              className="btn btn-accent"
            >
              Valider les réponses
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default CharactersRound;
