import React, { useRef, useState } from 'react';
import AnswerAutocomplete from '@/components/game/AnswerAutocomplete';
import { Theme } from '@/types';

/** Issue d'un envoi, telle que le mode l'interprete. */
export interface GuessFeedback {
  /** Vider le champ : l'essai a ete consomme. */
  clear: boolean;
  /** Mauvaise reponse ou proposition refusee : le champ secoue. */
  wrong?: boolean;
}

interface GuessFormProps {
  theme: Theme;
  suggest: (input: string) => string[];
  onSubmit: (value: string) => Promise<GuessFeedback>;
  busy: boolean;
  placeholder?: string;
  submitLabel?: string;
  onSkip?: () => void;
  skipLabel?: string;
  /** Texte discret sous la barre (regle du mode, potentiel...). */
  hint?: React.ReactNode;
}

/**
 * Barre de reponse, commune aux modes a reponse unique : le champ et son bouton ne font
 * qu'un bloc, comme une barre de recherche. On tape, Tab complete, Entree valide.
 */
const GuessForm: React.FC<GuessFormProps> = ({
  suggest,
  onSubmit,
  busy,
  placeholder = 'Titre de l’œuvre…',
  submitLabel = 'Valider',
  onSkip,
  skipLabel = 'Passer',
  hint,
}) => {
  const [value, setValue] = useState('');
  const [shaking, setShaking] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const submit = async () => {
    const answer = value.trim();
    if (!answer || busy) {
      return;
    }
    const feedback = await onSubmit(answer);
    if (feedback.clear) {
      setValue('');
    }
    if (feedback.wrong) {
      setShaking(true);
    }
    // Le champ reprend la main : on enchaine les essais au clavier. Valider avec le bouton
    // ou la souris y deplacait le focus, et une manche de 22 essais imposait autant de clics.
    inputRef.current?.focus();
  };

  return (
    <form
      className="space-y-2"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <div
        // La classe est retiree en fin d'animation, pour qu'une erreur suivante la rejoue.
        onAnimationEnd={(e) => e.target === e.currentTarget && setShaking(false)}
        className={`flex border-2 border-ink bg-sheet transition-shadow focus-within:shadow-hard ${
          shaking ? 'animate-shake' : ''
        }`}
      >
        <AnswerAutocomplete
          className="min-w-0 flex-1"
          inputClassName="block w-full bg-transparent px-3 py-3 text-base text-ink placeholder:text-muted/70 focus:outline-none"
          inputRef={inputRef}
          value={value}
          onChange={setValue}
          suggest={suggest}
          onSubmit={() => void submit()}
          placeholder={placeholder}
          // Jamais desactive pendant l'envoi : le navigateur retire le focus d'un champ
          // desactive et ne le rend pas, ce qui obligeait a recliquer dedans a chaque essai.
          // La double soumission reste bloquee par `submit` et par le bouton.
          autoFocus
        />
        <button
          type="submit"
          disabled={busy || !value.trim()}
          className="flex-none border-l-2 border-ink bg-accent px-4 text-sm font-bold uppercase tracking-wide text-on-accent transition-opacity disabled:opacity-40"
        >
          {submitLabel} <span aria-hidden="true">↵</span>
        </button>
      </div>
      {(hint || onSkip) && (
        <div className="flex items-start justify-between gap-3 text-xs text-muted">
          <span>{hint}</span>
          {onSkip && (
            <button type="button" disabled={busy} onClick={onSkip} className="link-quiet flex-none !text-xs">
              {skipLabel}
            </button>
          )}
        </div>
      )}
    </form>
  );
};

export default GuessForm;
