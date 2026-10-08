import React, { useId, useMemo, useState } from 'react';

interface AnswerAutocompleteProps {
  value: string;
  onChange: (value: string) => void;
  /** Filtre local de l'index, fourni par useAutocomplete. */
  suggest: (input: string) => string[];
  /** Appele sur Entree quand aucune suggestion n'est surlignee. */
  onSubmit?: (value: string) => void;
  /** Entree appelle onSubmit meme champ vide (mode Personnages : passer au champ suivant). */
  submitEmpty?: boolean;
  placeholder?: string;
  disabled?: boolean;
  autoFocus?: boolean;
  className?: string;
  /** Remplace le style du champ (la barre de reponse porte son propre cadre). */
  inputClassName?: string;
  /** Laisse le parent rendre le focus au champ apres une soumission. */
  inputRef?: React.RefObject<HTMLInputElement>;
}

/** Retire accents et casse, pour reperer la saisie dans une suggestion. */
function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Met en gras la partie de la suggestion qui correspond a la saisie : on voit d'un coup
 * d'oeil pourquoi elle est proposee (utile quand elle vient d'un alias, ou elle n'apparait pas).
 */
const Highlighted: React.FC<{ text: string; query: string }> = ({ text, query }) => {
  const needle = fold(query.trim());
  // fold() conserve la longueur des lettres latines usuelles : les indices restent alignes.
  const at = needle ? fold(text).indexOf(needle) : -1;
  if (at < 0 || fold(text).length !== text.length) {
    return <>{text}</>;
  }
  return (
    <>
      {text.slice(0, at)}
      <strong className="font-bold underline decoration-accent decoration-2 underline-offset-2">
        {text.slice(at, at + needle.length)}
      </strong>
      {text.slice(at + needle.length)}
    </>
  );
};

/**
 * Champ de reponse avec suggestions.
 *
 * Choisir une suggestion remplit le champ sans soumettre : dans les modes a essai unique,
 * un clic malheureux dans la liste ne doit pas couter la manche. Au clavier : fleches pour
 * parcourir, Tab pour prendre la suggestion (la premiere si aucune n'est surlignee), Entree
 * pour valider.
 */
const AnswerAutocomplete: React.FC<AnswerAutocompleteProps> = ({
  value,
  onChange,
  suggest,
  onSubmit,
  submitEmpty = false,
  placeholder,
  disabled = false,
  autoFocus = false,
  className = '',
  inputClassName = 'field',
  inputRef,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const listId = useId();

  const suggestions = useMemo(() => (isOpen ? suggest(value) : []), [isOpen, suggest, value]);

  const choose = (entry: string) => {
    onChange(entry);
    setIsOpen(false);
    setHighlighted(-1);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown' && suggestions.length > 0) {
      e.preventDefault();
      setHighlighted((index) => (index + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp' && suggestions.length > 0) {
      e.preventDefault();
      setHighlighted((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
    } else if (e.key === 'Tab' && !e.shiftKey && suggestions.length > 0 && value.trim()) {
      // Completer plutot que quitter le champ : c'est ce qu'on attend en tapant un titre.
      e.preventDefault();
      choose(suggestions[Math.max(0, highlighted)]);
    } else if (e.key === 'Escape') {
      setIsOpen(false);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (highlighted >= 0 && highlighted < suggestions.length) {
        choose(suggestions[highlighted]);
      } else if (onSubmit && (value.trim() || submitEmpty)) {
        setIsOpen(false);
        onSubmit(value.trim());
      }
    }
  };

  return (
    <div className={`relative ${className}`}>
      <input
        ref={inputRef}
        type="text"
        role="combobox"
        aria-expanded={suggestions.length > 0}
        aria-controls={listId}
        aria-autocomplete="list"
        autoComplete="off"
        spellCheck={false}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        onChange={(e) => {
          onChange(e.target.value);
          setIsOpen(true);
          setHighlighted(-1);
        }}
        onFocus={() => setIsOpen(true)}
        // Delai : laisse le clic sur une suggestion aboutir avant la fermeture de la liste.
        onBlur={() => window.setTimeout(() => setIsOpen(false), 150)}
        onKeyDown={handleKeyDown}
        className={inputClassName}
      />

      {suggestions.length > 0 && (
        <ul
          id={listId}
          role="listbox"
          className="absolute left-0 right-0 z-30 mt-1 max-h-72 animate-rise overflow-auto border-2 border-ink bg-sheet shadow-hard"
        >
          {suggestions.map((entry, index) => (
            <li
              // Deux oeuvres distinctes peuvent partager un libelle : l'index seul n'est pas unique.
              key={`${index}-${entry}`}
              role="option"
              aria-selected={index === highlighted}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(entry);
              }}
              // mousemove et non mouseenter : la liste s'ouvre souvent sous une souris immobile,
              // qui surlignait alors une ligne au hasard — et Tab la prenait au lieu de la premiere.
              onMouseMove={() => highlighted !== index && setHighlighted(index)}
              className={`flex cursor-pointer items-center justify-between gap-3 border-b border-dashed border-ink/25 px-3 py-2 text-sm last:border-b-0 ${
                index === highlighted ? 'bg-ink text-paper' : ''
              }`}
            >
              <span className="truncate">
                <Highlighted text={entry} query={value} />
              </span>
              {(index === highlighted || (highlighted < 0 && index === 0)) && (
                <kbd className="flex-none text-[0.65rem] font-bold uppercase opacity-60">Tab</kbd>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default AnswerAutocomplete;
