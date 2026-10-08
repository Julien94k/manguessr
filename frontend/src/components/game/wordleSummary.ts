import { WordleCell, WordleRow } from '@/types';

interface Bounds {
  exact: string | null;
  min: number | null;
  max: number | null;
}

/** Ce que les essais ont deja appris sur l'oeuvre a trouver, colonne par colonne. */
export interface WordleSummary {
  year: Bounds;
  score: Bounds;
  credits: string[];
  source: string[];
  genres: string[];
  tags: string[];
  partialTags: string[];
}

/**
 * Bornes deduites d'une colonne numerique : chaque fleche UP releve le minimum, chaque
 * fleche DOWN abaisse le maximum. Une valeur exacte l'emporte sur tout.
 */
function numericBounds(cells: WordleCell[]): Bounds {
  const bounds: Bounds = { exact: null, min: null, max: null };

  for (const cell of cells) {
    const value = Number.parseFloat(cell.value);
    if (cell.status === 'MATCH') {
      bounds.exact = cell.value;
    } else if (!Number.isNaN(value) && cell.compare === 'UP') {
      bounds.min = bounds.min === null ? value : Math.max(bounds.min, value);
    } else if (!Number.isNaN(value) && cell.compare === 'DOWN') {
      bounds.max = bounds.max === null ? value : Math.min(bounds.max, value);
    }
  }
  return bounds;
}

/**
 * Libelle d'une borne : « > 2012 », « < 84 », ou l'intervalle restant « 2013–2019 ».
 * Annee et score sont entiers : les bornes exclues deviennent un intervalle inclus, plus lisible.
 * Nul si rien n'est connu.
 */
export function describeBounds(bounds: Bounds): string | null {
  if (bounds.exact) {
    return bounds.exact;
  }
  if (bounds.min !== null && bounds.max !== null) {
    const low = Math.floor(bounds.min) + 1;
    const high = Math.ceil(bounds.max) - 1;
    return low >= high ? String(low) : `${low}–${high}`;
  }
  if (bounds.min !== null) {
    return `> ${bounds.min}`;
  }
  if (bounds.max !== null) {
    return `< ${bounds.max}`;
  }
  return null;
}

function confirmed(cells: WordleCell[], status: 'MATCH' | 'PARTIAL'): string[] {
  return [...new Set(cells.filter((cell) => cell.status === status).map((cell) => cell.value))];
}

export function summarizeWordle(rows: WordleRow[]): WordleSummary {
  const tags = rows.flatMap((row) => row.tags);
  const matchedTags = confirmed(tags, 'MATCH');
  return {
    year: numericBounds(rows.map((row) => row.year)),
    score: numericBounds(rows.map((row) => row.score)),
    credits: confirmed(rows.flatMap((row) => row.credit), 'MATCH'),
    source: confirmed(rows.map((row) => row.source), 'MATCH'),
    genres: confirmed(rows.flatMap((row) => row.genres), 'MATCH'),
    tags: matchedTags,
    partialTags: confirmed(tags, 'PARTIAL').filter((tag) => !matchedTags.includes(tag)),
  };
}
