/** Les deux univers jouables. Pilote l'accent de couleur et la liste des modes. */
export type Theme = 'anime' | 'manga';

/** Modes de jeu. Les valeurs correspondent aux enums GameMode cote backend. */
export type GameModeId =
  | 'IMAGES'
  | 'CHARACTERS'
  | 'OPENING'
  | 'ENDING'
  | 'WORDLE'
  | 'COVER_REVEAL';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface AuthResponse {
  token: string;
  role: string;
  username: string;
}

export interface UserProfile {
  id: number;
  email: string;
  username: string;
  role: string;
  createdAt: string;
}

/**
 * Une suggestion de l'index : une serie entiere n'en occupe qu'une. Ses autres titres sont des
 * `aliases`, qui ne s'affichent pas mais que la saisie trouve.
 */
export interface AutocompleteEntry {
  label: string;
  aliases?: string[];
}

/** Index d'autocompletion complet, servi en une fois puis filtre cote client. */
export interface AutocompleteResponse {
  type: Theme;
  entries: AutocompleteEntry[];
  count: number;
}

/** Un mode tel que decrit par GET /api/games. */
export interface GameModeInfo {
  id: GameModeId;
  title: string;
  description: string;
  rounds: number;
  maxScore: number;
  maxAttempts: number;
  playable: boolean;
}

export type RoundStatus = 'IN_PROGRESS' | 'SOLVED' | 'FAILED' | 'SKIPPED';
export type SessionStatus = 'IN_PROGRESS' | 'FINISHED';

/** Media servi par le proxy a jeton signe. */
export interface MediaRef {
  kind: 'IMAGE' | 'AUDIO' | 'VIDEO';
  url: string;
}

export interface Clue {
  index: number;
  label: string;
  cost: number;
  unlocked: boolean;
  free: boolean;
}

/** Portrait du mode Personnages : nom et oeuvre restent nuls tant que la manche est ouverte. */
export interface CharacterSlot {
  slot: number;
  imageUrl: string;
  name: string | null;
  workTitle: string | null;
  nameFound: boolean;
  titleFound: boolean;
}

export type CellStatus = 'MATCH' | 'PARTIAL' | 'MISS';

/** {@code compare} : UP signifie que l'oeuvre a trouver a une valeur plus grande. */
export interface WordleCell {
  value: string;
  status: CellStatus;
  compare: 'UP' | 'DOWN' | null;
}

export interface WordleRow {
  title: string;
  correct: boolean;
  year: WordleCell;
  credit: WordleCell[];
  source: WordleCell;
  score: WordleCell;
  genres: WordleCell[];
  tags: WordleCell[];
}

export interface Solution {
  title: string;
  altTitle: string | null;
  year: number | null;
  coverUrl: string | null;
  externalUrl: string | null;
  songTitle: string | null;
  artist: string | null;
}

export interface Round {
  ordinal: number;
  difficulty: Difficulty;
  status: RoundStatus;
  attemptsUsed: number;
  maxAttempts: number;
  score: number;
  maxScore: number;
  media: MediaRef[];
  clues: Clue[];
  characters: CharacterSlot[];
  wordleRows: WordleRow[];
  titleHint: string | null;
  synopsis: string | null;
  topCharacter: string | null;
  solution: Solution | null;
}

export interface GameSession {
  id: number;
  mode: GameModeId;
  theme: 'ANIME' | 'MANGA';
  unlimited: boolean;
  playDate: string;
  status: SessionStatus;
  totalScore: number;
  maxScore: number;
  rounds: Round[];
}

export interface CharacterGuess {
  character: string;
  title: string;
}

export interface GuessResult {
  correct: boolean;
  message: string | null;
  round: Round;
  session: GameSession;
}

/** Contrat commun des composants de manche (tous les modes sauf Personnages). */
export interface RoundComponentProps {
  round: Round;
  theme: Theme;
  busy: boolean;
  /** Filtre local de l'index des titres. */
  suggest: (input: string) => string[];
  onGuess: (answer: string) => Promise<GuessResult | null>;
  onClue: () => void;
  onSkip: () => void;
}

/** Ce que chaque mode a reellement de quoi jouer, apres ingestion. */
export interface CatalogStats {
  animeCount: number;
  mangaCount: number;
  worksWithEnoughImages: number;
  worksWithEnoughCharacters: number;
  animeWithThemes: number;
  mangaWithChapterPages: number;
}

export type DailyModeState = 'NOT_STARTED' | 'IN_PROGRESS' | 'FINISHED';

/** Etat d'un mode dans le defi du jour. Toujours NOT_STARTED pour un joueur anonyme. */
export interface DailyMode {
  mode: GameModeId;
  title: string;
  status: DailyModeState;
  sessionId: number | null;
  score: number;
  maxScore: number;
}

export interface DailyOverview {
  date: string;
  secondsUntilNext: number;
  theme: Theme;
  totalScore: number;
  maxScore: number;
  /** Nulle sans compte. */
  currentStreak: number | null;
  modes: DailyMode[];
}

export type LeaderboardPeriod = 'daily' | 'weekly' | 'alltime';

export interface LeaderboardEntry {
  rank: number;
  username: string;
  score: number;
  games: number;
}

export interface Leaderboard {
  period: LeaderboardPeriod;
  theme: Theme | 'all';
  from: string;
  to: string;
  entries: LeaderboardEntry[];
  /** Ligne du joueur connecte, meme hors du haut du classement. */
  me: LeaderboardEntry | null;
}

export interface ModeStats {
  mode: GameModeId;
  theme: 'ANIME' | 'MANGA';
  title: string;
  played: number;
  bestScore: number;
  averageScore: number;
  maxScore: number;
}

export interface HistoryEntry {
  sessionId: number;
  playDate: string;
  mode: GameModeId;
  theme: 'ANIME' | 'MANGA';
  title: string;
  score: number;
  maxScore: number;
}

export interface PlayerStats {
  currentStreak: number;
  bestStreak: number;
  daysPlayed: number;
  gamesPlayed: number;
  totalScore: number;
  modes: ModeStats[];
  history: HistoryEntry[];
}
