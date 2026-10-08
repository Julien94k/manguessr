import { DailyMode, DailyOverview, GameModeId, GameSession, Round } from '@/types';

/**
 * Repere de chaque mode, facon rubrique de magazine japonais : un kanji qui dit ce qu'on y
 * fait (画 image, 人 personne, 歌 chanson, 終 fin, 謎 enigme, 表 couverture).
 */
export const MODE_MARK: Record<GameModeId, string> = {
  IMAGES: '画',
  CHARACTERS: '人',
  OPENING: '歌',
  ENDING: '終',
  WORDLE: '謎',
  COVER_REVEAL: '表',
};

/** Premier defi en ligne : le numero du jour se compte depuis cette date, comme un magazine. */
const FIRST_ISSUE = Date.UTC(2026, 8, 13);

/** Numero du defi du jour (AAAA-MM-JJ, UTC). */
export function issueNumber(playDate: string): number {
  return Math.floor((Date.parse(`${playDate}T00:00:00Z`) - FIRST_ISSUE) / 86_400_000) + 1;
}

/** Date de jeu en toutes lettres : « mardi 23 septembre ». */
export function formatLongDate(playDate: string): string {
  return new Date(`${playDate}T00:00:00Z`).toLocaleDateString('fr-FR', {
    timeZone: 'UTC',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

export function formatPoints(value: number): string {
  return value.toLocaleString('fr-FR');
}

export const DIFFICULTY_LABEL = {
  EASY: 'Facile',
  MEDIUM: 'Moyen',
  HARD: 'Difficile',
} as const;

const MODE_IDS: GameModeId[] = ['IMAGES', 'CHARACTERS', 'OPENING', 'ENDING', 'WORDLE', 'COVER_REVEAL'];

/** Identifiant de mode -> segment d'URL lisible : COVER_REVEAL -> cover-reveal. */
export function modeToSlug(mode: GameModeId): string {
  return mode.toLowerCase().replace('_', '-');
}

export function slugToMode(slug: string | undefined): GameModeId | null {
  if (!slug) {
    return null;
  }
  const candidate = slug.toUpperCase().replace('-', '_');
  return MODE_IDS.find((mode) => mode === candidate) ?? null;
}

/** Date du jour en UTC : le puzzle quotidien change a minuit UTC, pas a minuit local. */
export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Date de jeu (AAAA-MM-JJ, UTC) au format francais, sans decalage de fuseau. */
export function formatPlayDate(playDate: string): string {
  return new Date(`${playDate}T00:00:00Z`).toLocaleDateString('fr-FR', { timeZone: 'UTC' });
}

/** Duree en secondes -> « 05:42:09 ». */
export function formatCountdown(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  return [hours, minutes, seconds].map((part) => String(part).padStart(2, '0')).join(':');
}

function roundSquare(round: Round): string {
  if (round.status === 'SOLVED') {
    return '🟩';
  }
  return round.score > 0 ? '🟨' : '🟥';
}

/**
 * Texte de partage d'une partie terminee, a la Wordle : un carre par manche, sans jamais
 * citer l'oeuvre — le partager ne doit pas gacher le puzzle des autres.
 */
export function buildShareText(session: GameSession, modeTitle: string, url: string): string {
  const universe = session.theme === 'ANIME' ? 'Anime' : 'Manga';
  const when = session.unlimited ? 'partie libre' : formatPlayDate(session.playDate);
  const squares = session.rounds.map(roundSquare).join('');
  const first = session.rounds[0];
  const detail =
    session.mode === 'WORDLE' && first?.status === 'SOLVED'
      ? ` · trouvé en ${first.attemptsUsed} essai${first.attemptsUsed > 1 ? 's' : ''}`
      : '';

  return [
    `ManGuessr · ${modeTitle} (${universe}) · ${when}`,
    `${squares} ${formatPoints(session.totalScore)} / ${formatPoints(session.maxScore)}${detail}`,
    url,
  ].join('\n');
}

/**
 * Prochain mode a jouer dans le defi du jour : le premier non termine apres `current`, en
 * reprenant au debut de la liste. Nul quand tout est joue.
 *
 * @param playable modes jouables de l'univers : un mode sans catalogue ne bloque pas l'enchainement
 */
export function nextDailyMode(
  overview: DailyOverview,
  playable: GameModeId[],
  current: GameModeId | null
): DailyMode | null {
  const modes = overview.modes.filter((entry) => playable.includes(entry.mode));
  const start = current === null ? 0 : modes.findIndex((entry) => entry.mode === current) + 1;
  const ordered = [...modes.slice(start), ...modes.slice(0, start)];
  return ordered.find((entry) => entry.status !== 'FINISHED' && entry.mode !== current) ?? null;
}
