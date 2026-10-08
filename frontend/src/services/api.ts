import apiClient from '@/services/apiClient';
import {
  AutocompleteResponse,
  AuthResponse,
  CatalogStats,
  CharacterGuess,
  DailyOverview,
  GameModeId,
  GameModeInfo,
  GameSession,
  GuessResult,
  Leaderboard,
  LeaderboardPeriod,
  PlayerStats,
  Round,
  Theme,
  UserProfile,
} from '@/types';

export async function register(payload: {
  email: string;
  username: string;
  password: string;
}): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/register', payload);
  return data;
}

export async function login(payload: { email: string; password: string }): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>('/auth/login', payload);
  return data;
}

export async function fetchMe(): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/auth/me');
  return data;
}

/**
 * Version de l'index d'autocompletion : 6 = une entree par serie ({label, aliases}) au lieu
 * d'une liste de titres. A incrementer aussi bien quand le contenu change que la forme.
 */
const AUTOCOMPLETE_INDEX_VERSION = 7;

/**
 * Index d'autocompletion d'un univers.
 *
 * La liste complete est recuperee en une fois (gzip cote serveur, cache une heure) puis
 * filtree localement : c'est instantane a la frappe et evite une requete par caractere.
 */
export async function fetchAutocomplete(
  type: Theme,
  target: 'title' | 'character' = 'title'
): Promise<AutocompleteResponse> {
  const { data } = await apiClient.get<AutocompleteResponse>('/autocomplete', {
    // v : version du contenu de l'index. A incrementer quand son filtrage change, sinon le
    // navigateur sert l'ancienne liste, mise en cache une heure (Cache-Control public).
    params: { type, target, v: AUTOCOMPLETE_INDEX_VERSION },
  });
  return data;
}

export async function fetchCatalogStats(): Promise<CatalogStats> {
  const { data } = await apiClient.get<CatalogStats>('/catalog/stats');
  return data;
}

/** Modes d'un univers, avec leur jouabilite au vu du catalogue. */
export async function fetchGames(theme: Theme): Promise<GameModeInfo[]> {
  const { data } = await apiClient.get<GameModeInfo[]>('/games', { params: { theme } });
  return data;
}

/** Demarre une partie ; pour un joueur connecte, reprend celle du jour si elle existe. */
export async function startSession(payload: {
  mode: GameModeId;
  theme: Theme;
  unlimited: boolean;
}): Promise<GameSession> {
  const { data } = await apiClient.post<GameSession>('/sessions', payload);
  return data;
}

export async function fetchSession(id: number): Promise<GameSession> {
  const { data } = await apiClient.get<GameSession>(`/sessions/${id}`);
  return data;
}

export async function submitGuess(
  sessionId: number,
  payload: { roundOrdinal: number; answer?: string; entries?: CharacterGuess[] }
): Promise<GuessResult> {
  const { data } = await apiClient.post<GuessResult>(`/sessions/${sessionId}/guess`, payload);
  return data;
}

export async function unlockClue(sessionId: number, round: number): Promise<Round> {
  const { data } = await apiClient.post<Round>(`/sessions/${sessionId}/clue`, null, {
    params: { round },
  });
  return data;
}

export async function skipRound(sessionId: number, round: number): Promise<GameSession> {
  const { data } = await apiClient.post<GameSession>(`/sessions/${sessionId}/skip`, null, {
    params: { round },
  });
  return data;
}

/** Defi du jour d'un univers ; la progression n'est renseignee que pour un joueur connecte. */
export async function fetchDaily(theme: Theme): Promise<DailyOverview> {
  const { data } = await apiClient.get<DailyOverview>('/daily', { params: { theme } });
  return data;
}

export async function fetchLeaderboard(
  period: LeaderboardPeriod,
  theme: Theme | 'all'
): Promise<Leaderboard> {
  const { data } = await apiClient.get<Leaderboard>('/leaderboard', { params: { period, theme } });
  return data;
}

export async function fetchPlayerStats(): Promise<PlayerStats> {
  const { data } = await apiClient.get<PlayerStats>('/profile/stats');
  return data;
}
