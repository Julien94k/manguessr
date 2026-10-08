import { useEffect, useMemo, useState } from 'react';
import { fetchAutocomplete } from '@/services/api';
import { AutocompleteEntry, Theme } from '@/types';

/** Nombre de suggestions affichees sous le champ de reponse. */
const MAX_SUGGESTIONS = 5;

/**
 * Forme de comparaison : sans accents, sans casse, apostrophes supprimees, ponctuation remplacee
 * par une espace. Miroir de TextNormalizer cote serveur, pour que « pokemon » trouve « Pokémon »
 * et « jojos » trouve « JoJo's ».
 */
function normalize(value: string): string {
  return value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/['\u2018\u2019\u02bc`\u00b4]/g, '')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

/**
 * Une suggestion prete a filtrer : son libelle, et toutes les formes normalisees qui y menent
 * (le libelle et ses alias). Une serie se trouve donc par n'importe lequel de ses titres, mais
 * ne s'affiche que sous un seul.
 */
interface IndexedEntry {
  entry: string;
  keys: string[];
}

/**
 * Charge l'index d'autocompletion d'un univers et expose une fonction de filtrage locale.
 *
 * L'index complet tient en quelques centaines de kilo-octets gzippes et ne change qu'a
 * l'ingestion : le charger une fois puis filtrer en memoire evite une requete par frappe.
 */
export function useAutocomplete(theme: Theme, target: 'title' | 'character' = 'title') {
  const [entries, setEntries] = useState<AutocompleteEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchAutocomplete(theme, target)
      .then((response) => {
        if (!cancelled) {
          setEntries(response.entries);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Index indisponible.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    // Un changement d'univers en cours de chargement ne doit pas ecraser le nouvel index.
    return () => {
      cancelled = true;
    };
  }, [theme, target]);

  // Normalisation faite une fois par index, pas a chaque frappe (plus de 6000 titres par univers).
  const index = useMemo<IndexedEntry[]>(
    () =>
      entries.map((entry) => ({
        entry: entry.label,
        keys: [entry.label, ...(entry.aliases ?? [])].map(normalize),
      })),
    [entries]
  );

  /**
   * Suggestions pour une saisie : les titres qui commencent par les lettres tapees, puis, pour
   * completer, ceux dont un mot commence par elles (« titan » -> « Attack on Titan »).
   * Les plus courts d'abord : « Naruto » avant « Nanatsu no Taizai ».
   *
   * Un alias vaut son libelle : taper « boruto » propose « NARUTO », qui est la reponse
   * attendue depuis que les suites appartiennent a la serie.
   */
  const suggest = useMemo(() => {
    return (input: string): string[] => {
      const needle = normalize(input);
      if (!needle) {
        return [];
      }

      const titleStarts: IndexedEntry[] = [];
      const wordStarts: IndexedEntry[] = [];
      for (const item of index) {
        if (item.keys.some((key) => key.startsWith(needle))) {
          titleStarts.push(item);
        } else if (item.keys.some((key) => key.includes(` ${needle}`))) {
          wordStarts.push(item);
        }
      }

      // Longueur du libelle : c'est ce que le joueur lit, et un alias tres court ne doit pas
      // faire remonter une serie au-dessus d'un titre qui correspond mieux.
      const byLength = (a: IndexedEntry, b: IndexedEntry) =>
        a.entry.length - b.entry.length || a.entry.localeCompare(b.entry);

      // Une seule suggestion par libelle normalise : « DEATH NOTE » et « Death Note » font doublon.
      const seen = new Set<string>();
      const result: string[] = [];
      for (const item of [...titleStarts.sort(byLength), ...wordStarts.sort(byLength)]) {
        const label = normalize(item.entry);
        if (!seen.has(label)) {
          seen.add(label);
          result.push(item.entry);
          if (result.length === MAX_SUGGESTIONS) {
            break;
          }
        }
      }
      return result;
    };
  }, [index]);

  return { entries, suggest, isLoading, error };
}
