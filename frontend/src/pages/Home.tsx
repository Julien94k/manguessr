import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAppStore } from '@/store/useAppStore';
import DailyCountdown from '@/components/game/DailyCountdown';
import {
  MODE_MARK,
  formatLongDate,
  formatPoints,
  issueNumber,
  modeToSlug,
  nextDailyMode,
} from '@/components/game/gameUi';
import { useDailyProgress } from '@/hooks/useDailyProgress';
import { useGames } from '@/hooks/useGames';
import { DailyMode, GameModeInfo } from '@/types';

type PlayKind = 'daily' | 'free';

const PLAY_KIND_KEY = 'manguessr-play-kind';

/** Derniere bascule choisie, par confort : l'acces peut echouer (navigation privee). */
function loadPlayKind(): PlayKind {
  try {
    return localStorage.getItem(PLAY_KIND_KEY) === 'free' ? 'free' : 'daily';
  } catch {
    return 'daily';
  }
}

/**
 * Une ligne du sommaire : un mode, entierement cliquable.
 *
 * Plus de boutons par mode : la bascule en tete de liste dit si l'on joue le defi du jour ou
 * une partie libre, et la ligne entiere y mene. A droite, ce qu'il y a a gagner, ou le score
 * deja obtenu. Au survol, la ligne s'imprime en negatif.
 */
const ContentsEntry: React.FC<{ mode: GameModeInfo; kind: PlayKind; progress: DailyMode | undefined }> = ({
  mode,
  kind,
  progress,
}) => {
  const total = mode.maxScore * mode.rounds;
  const status = kind === 'daily' ? progress?.status : undefined;

  const body = (
    <>
      <span aria-hidden="true" className="w-10 flex-none text-center font-display text-3xl sm:w-12 sm:text-4xl">
        {MODE_MARK[mode.id]}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-display text-lg leading-tight sm:text-2xl">{mode.title}</span>
        <span className="block truncate text-sm opacity-70">{mode.description}</span>
      </span>
      <span className="flex-none text-right">
        {status === 'FINISHED' ? (
          <span className="font-display text-lg sm:text-xl">
            ✓ {formatPoints(progress?.score ?? 0)}
            <span className="ml-1 font-sans text-xs font-bold opacity-70">pts</span>
          </span>
        ) : status === 'IN_PROGRESS' ? (
          <span className="text-sm font-bold uppercase tracking-wide">En cours →</span>
        ) : mode.playable ? (
          <span className="inline-flex items-baseline gap-3">
            <span className="hidden tabular-nums opacity-70 sm:inline">
              {kind === 'daily' ? `${formatPoints(total)} pts` : 'illimité'}
            </span>
            <span className="text-2xl transition-transform group-hover:translate-x-1">→</span>
          </span>
        ) : (
          <span className="text-xs">Bientôt</span>
        )}
      </span>
    </>
  );

  const row = 'group flex items-center gap-4 px-2 py-4 sm:gap-6 sm:px-4 sm:py-5';
  return (
    <li className="border-b-2 border-ink last:border-b-0">
      {mode.playable ? (
        <Link
          to={`/play/${modeToSlug(mode.id)}${kind === 'free' ? '?libre=1' : ''}`}
          className={`${row} transition-colors hover:bg-ink hover:text-paper focus-visible:bg-ink focus-visible:text-paper`}
        >
          {body}
        </Link>
      ) : (
        <div className={`${row} opacity-40`} title="Catalogue insuffisant pour l'instant">
          {body}
        </div>
      )}
    </li>
  );
};

const KINDS: { id: PlayKind; label: string }[] = [
  { id: 'daily', label: 'Défi du jour' },
  { id: 'free', label: 'Entraînement' },
];

const Home: React.FC = () => {
  const theme = useAppStore((state) => state.theme);
  const isLoggedIn = useAppStore((state) => state.token !== null);
  const { modes, isLoading, error } = useGames(theme);
  const { overview: daily, reload: loadDaily } = useDailyProgress(theme);
  const [kind, setKind] = useState<PlayKind>(loadPlayKind);
  const chooseKind = (next: PlayKind) => {
    setKind(next);
    try {
      localStorage.setItem(PLAY_KIND_KEY, next);
    } catch {
      // Preference de confort : sans stockage, on repartira du defi du jour.
    }
  };

  const dailyByMode = new Map((daily?.modes ?? []).map((entry) => [entry.mode, entry]));
  const playableIds = modes.filter((mode) => mode.playable).map((mode) => mode.id);
  const nextMode = daily ? nextDailyMode(daily, playableIds, null) : null;
  const dailyStarted = (daily?.modes ?? []).some((entry) => entry.status !== 'NOT_STARTED');
  const dailyMax = (daily?.modes ?? [])
    .filter((entry) => playableIds.includes(entry.mode))
    .reduce((total, entry) => total + entry.maxScore, 0);

  return (
    <div className="space-y-12">
      {/* Couverture du numero du jour. */}
      <section className="panel speedlines relative overflow-hidden shadow-hard-lg">
        <div className="screentone pointer-events-none absolute -right-10 -top-10 h-64 w-64 rotate-12" aria-hidden="true" />

        <div className="relative grid gap-8 p-6 sm:p-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-end">
          <div>
            <p className="kicker">
              {daily ? (
                <>
                  N°{issueNumber(daily.date)} · {formatLongDate(daily.date)} · édition {theme}
                </>
              ) : (
                <>Édition {theme}</>
              )}
            </p>
            <h1 className="mt-4 font-display text-4xl leading-[1.05] sm:text-6xl">
              Devinez
              <br />
              <span className="inline-block -rotate-1 bg-accent px-2 text-on-accent">{theme === 'anime' ? "l'anime" : 'le manga'}</span>
              <br />
              du jour.
            </h1>
            <p className="mt-5 max-w-md text-muted">
              Un défi par mode, le même pour tout le monde, renouvelé chaque nuit à minuit UTC.
              {!isLoggedIn && (
                <>
                  {' '}
                  <Link to="/register" className="font-bold text-ink underline decoration-accent decoration-2 underline-offset-4">
                    Créez un compte
                  </Link>{' '}
                  pour entrer au classement et tenir votre série.
                </>
              )}
            </p>

            {daily && playableIds.length > 0 && (
              <div className="mt-7">
                {nextMode ? (
                  <Link to={`/play/${modeToSlug(nextMode.mode)}`} className="btn btn-accent btn-lg">
                    {dailyStarted ? 'Continuer le défi' : 'Commencer le défi'} →
                  </Link>
                ) : (
                  <p className="font-display text-xl text-accent">Défi du jour bouclé. À demain !</p>
                )}
              </div>
            )}
          </div>

          {daily && (
            <dl className="border-2 border-ink bg-paper">
              <div className="border-b-2 border-ink px-4 py-3">
                <dt className="kicker">Votre score du jour</dt>
                <dd className="font-display text-3xl tabular-nums">
                  {formatPoints(daily.totalScore)}
                  <span className="ml-1 font-sans text-sm font-medium text-muted">/ {formatPoints(dailyMax)}</span>
                </dd>
              </div>
              <div className="grid grid-cols-2">
                <div className="border-r-2 border-ink px-4 py-3">
                  <dt className="kicker">Série</dt>
                  <dd className="font-display text-xl">
                    {daily.currentStreak !== null ? `${daily.currentStreak} j` : '—'}
                  </dd>
                </div>
                <div className="px-4 py-3">
                  <dt className="kicker">Prochain n°</dt>
                  <dd className="text-xl font-bold">
                    <DailyCountdown key={daily.date} secondsUntilNext={daily.secondsUntilNext} onElapsed={loadDaily} />
                  </dd>
                </div>
              </div>
            </dl>
          )}
        </div>
      </section>

      {/* Sommaire des modes. */}
      <section>
        <div className="flex flex-wrap items-end justify-between gap-4 pb-3">
          <h2 className="font-display text-3xl">Sommaire</h2>
          <div role="radiogroup" aria-label="Type de partie" className="flex border-2 border-ink">
            {KINDS.map((option) => (
              <button
                key={option.id}
                type="button"
                role="radio"
                aria-checked={kind === option.id}
                onClick={() => chooseKind(option.id)}
                className={`px-3 py-1.5 text-sm font-bold ${
                  kind === option.id ? 'bg-ink text-paper' : 'bg-sheet text-muted hover:text-ink'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
        <p className="mb-3 text-sm text-muted">
          {kind === 'daily'
            ? 'Les mêmes manches pour tout le monde, une fois par jour. Compte pour le classement.'
            : 'Des parties à volonté, tirées au hasard. Hors classement.'}
        </p>

        {isLoading && <p className="py-10 text-center text-muted">Impression en cours…</p>}
        {error && <p className="py-10 text-center font-bold text-accent">{error}</p>}

        <ol className="border-y-[3px] border-ink">
          {modes.map((mode) => (
            <ContentsEntry key={`${theme}-${mode.id}`} mode={mode} kind={kind} progress={dailyByMode.get(mode.id)} />
          ))}
        </ol>
      </section>
    </div>
  );
};

export default Home;
