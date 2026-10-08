import React, { useMemo } from 'react';
import { describeBounds, summarizeWordle } from '@/components/game/wordleSummary';
import { CellStatus, Theme, WordleCell, WordleRow } from '@/types';

const STATUS_CLASS: Record<CellStatus, string> = {
  MATCH: 'bg-hit text-on-accent',
  // Texte encre fixe : le jaune reste clair dans les deux themes.
  PARTIAL: 'bg-near text-[#18140f]',
  MISS: 'bg-miss text-ink',
};

/** Borne deduite mais pas encore exacte : ni verte ni grise, pour ne pas passer pour un essai. */
const BOUND_CLASS = 'bg-sheet text-ink ring-2 ring-inset ring-ink';

const CHIP = 'inline-flex items-center gap-1 whitespace-nowrap px-2 py-1 text-xs font-bold';

/** UP : l'oeuvre a trouver a une valeur plus grande que la proposition. */
const ARROW = { UP: '↑', DOWN: '↓' } as const;

const Cell: React.FC<{ cell: WordleCell }> = ({ cell }) => (
  <span className={`${CHIP} ${STATUS_CLASS[cell.status]}`}>
    {cell.value || '—'}
    {cell.compare && <span aria-label={cell.compare === 'UP' ? 'plus grand' : 'plus petit'}>{ARROW[cell.compare]}</span>}
  </span>
);

const CellList: React.FC<{ cells: WordleCell[] }> = ({ cells }) =>
  cells.length === 0 ? (
    <span className="text-xs text-muted">—</span>
  ) : (
    <div className="flex flex-wrap gap-1">
      {cells.map((cell) => (
        <Cell key={cell.value} cell={cell} />
      ))}
    </div>
  );

/** Case encore inconnue du resume. */
const Unknown: React.FC = () => (
  <span className={`${CHIP} border-2 border-dashed border-ink/40 text-muted`}>?</span>
);

const BoundCell: React.FC<{ label: string | null; exact: boolean }> = ({ label, exact }) =>
  label === null ? <Unknown /> : <span className={`${CHIP} ${exact ? STATUS_CLASS.MATCH : BOUND_CLASS}`}>{label}</span>;

const KnownList: React.FC<{ matched: string[]; partial?: string[] }> = ({ matched, partial = [] }) =>
  matched.length === 0 && partial.length === 0 ? (
    <Unknown />
  ) : (
    <div className="flex flex-wrap gap-1">
      {matched.map((value) => (
        <span key={`m-${value}`} className={`${CHIP} ${STATUS_CLASS.MATCH}`}>{value}</span>
      ))}
      {partial.map((value) => (
        <span key={`p-${value}`} className={`${CHIP} ${STATUS_CLASS.PARTIAL}`}>{value}</span>
      ))}
    </div>
  );

/**
 * Ligne « Résumé » : ce que les essais ont appris, dans les memes colonnes et les memes
 * pastilles que les essais, pour se lire d'un coup d'oeil au-dessus d'eux.
 */
const SummaryRow: React.FC<{ rows: WordleRow[] }> = ({ rows }) => {
  const summary = useMemo(() => summarizeWordle(rows), [rows]);

  return (
    <tr className="screentone border-b-[3px] border-ink align-top">
      <td className="kicker px-3 py-2 !text-ink">
        Résumé
      </td>
      <td className="px-3 py-2"><BoundCell label={describeBounds(summary.year)} exact={summary.year.exact !== null} /></td>
      <td className="px-3 py-2"><KnownList matched={summary.credits} /></td>
      <td className="px-3 py-2"><KnownList matched={summary.source} /></td>
      <td className="px-3 py-2"><BoundCell label={describeBounds(summary.score)} exact={summary.score.exact !== null} /></td>
      <td className="px-3 py-2"><KnownList matched={summary.genres} /></td>
      <td className="px-3 py-2"><KnownList matched={summary.tags} partial={summary.partialTags} /></td>
    </tr>
  );
};

/** Table de comparaison du mode Wordle : le resume, puis le dernier essai en premier. */
const WordleTable: React.FC<{ rows: WordleRow[]; theme: Theme }> = ({ rows, theme }) => {
  const headers = ['Titre', 'Année', theme === 'anime' ? 'Studio' : 'Auteur', 'Source', 'Score', 'Genres', 'Tags'];

  return (
    <div className="overflow-x-auto border-2 border-ink bg-sheet shadow-hard">
      <table className="min-w-[56rem] w-full text-left text-sm">
        <thead className="bg-ink text-xs uppercase tracking-wider text-paper">
          <tr>
            {headers.map((header) => (
              <th key={header} className="px-3 py-2 font-bold">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-dashed divide-ink/30">
          <SummaryRow rows={rows} />
          {rows.map((row) => (
            <tr key={row.title} className="align-top">
              <td
                className={`px-3 py-2 font-medium ${
                  row.correct ? 'text-hit' : ''
                }`}
              >
                {row.title}
              </td>
              <td className="px-3 py-2"><Cell cell={row.year} /></td>
              <td className="px-3 py-2"><CellList cells={row.credit} /></td>
              <td className="px-3 py-2"><Cell cell={row.source} /></td>
              <td className="px-3 py-2"><Cell cell={row.score} /></td>
              <td className="px-3 py-2"><CellList cells={row.genres} /></td>
              <td className="px-3 py-2"><CellList cells={row.tags} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default WordleTable;
