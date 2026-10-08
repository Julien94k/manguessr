import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Card from '@/components/ui/Card';
import CharactersRound from '@/components/game/CharactersRound';
import CoverRevealRound from '@/components/game/CoverRevealRound';
import DailyTrack from '@/components/game/DailyTrack';
import ImageRound from '@/components/game/ImageRound';
import RoundResult from '@/components/game/RoundResult';
import ScoreBoard from '@/components/game/ScoreBoard';
import ThemeRound from '@/components/game/ThemeRound';
import WordleRound from '@/components/game/WordleRound';
import {
  DIFFICULTY_LABEL,
  MODE_MARK,
  buildShareText,
  issueNumber,
  formatPlayDate,
  formatPoints,
  modeToSlug,
  nextDailyMode,
  slugToMode,
} from '@/components/game/gameUi';
import { useAutocomplete } from '@/hooks/useAutocomplete';
import { useDailyProgress } from '@/hooks/useDailyProgress';
import { useGameSession } from '@/hooks/useGameSession';
import { useGames } from '@/hooks/useGames';
import { useAppStore } from '@/store/useAppStore';
import { DailyMode, DailyOverview, GameSession, Round, RoundComponentProps, Theme } from '@/types';

const GameSummary: React.FC<{
  session: GameSession;
  theme: Theme;
  modeTitle: string;
  onReplay: () => void;
  /** Defi du jour seulement : le mode a enchainer, ou nul quand tous sont joues. */
  daily: { overview: DailyOverview; next: DailyMode | null; nextTitle: string | null } | null;
}> = ({ session, modeTitle, onReplay, daily }) => {
  const [shareState, setShareState] = useState<'idle' | 'copied' | 'manual'>('idle');
  const shareText = buildShareText(session, modeTitle, window.location.origin);

  const share = async () => {
    try {
      // Le presse-papiers n'existe qu'en contexte securise (HTTPS ou localhost) : sur le Pi
      // servi en HTTP sur le reseau local, on affiche le texte a copier a la main.
      await navigator.clipboard.writeText(shareText);
      setShareState('copied');
    } catch {
      setShareState('manual');
    }
  };

  return (
    <Card className="space-y-6 text-center shadow-hard-lg">
      <div>
        <p className="kicker">Fin de partie</p>
        <p className="mt-2 font-display text-6xl tabular-nums text-accent">{formatPoints(session.totalScore)}</p>
        <p className="text-sm text-muted">sur {formatPoints(session.maxScore)} points</p>
      </div>

      <ul className="mx-auto max-w-sm border-y-2 border-ink text-left">
        {session.rounds.map((round) => (
          <li key={round.ordinal} className="flex justify-between gap-4 border-b border-dashed border-ink/40 py-2 text-sm last:border-b-0">
            <span>
              {session.rounds.length > 1 && `${DIFFICULTY_LABEL[round.difficulty]} · `}
              {round.solution?.title ?? (round.status === 'SKIPPED' ? 'Passée' : 'Personnages')}
            </span>
            <strong className="tabular-nums">{formatPoints(round.score)}</strong>
          </li>
        ))}
      </ul>

      {daily && !daily.next && (
        <div className="mx-auto max-w-sm border-2 border-dashed border-accent px-4 py-3">
          <p className="font-display text-lg">Défi du jour bouclé !</p>
          <p className="text-sm text-muted">
            Total de la journée :{' '}
            <strong className="text-accent">{formatPoints(daily.overview.totalScore)}</strong>
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-center gap-3">
        {daily?.next && (
          <Link to={`/play/${modeToSlug(daily.next.mode)}`} className="btn btn-accent">
            Mode suivant : {daily.nextTitle ?? daily.next.title} →
          </Link>
        )}
        <button type="button" onClick={() => void share()} className="btn btn-paper">
          {shareState === 'copied' ? 'Copié !' : 'Partager'}
        </button>
        <button
          type="button"
          onClick={onReplay}
          className={daily?.next ? 'btn btn-paper' : 'btn btn-accent'}
        >
          {session.unlimited ? 'Nouvelle partie' : 'Jouer en illimité'}
        </button>
        <Link to="/" className="link-quiet">
          Sommaire
        </Link>
      </div>

      {shareState === 'manual' && (
        <textarea
          readOnly
          rows={3}
          value={shareText}
          onFocus={(e) => e.currentTarget.select()}
          className="field mx-auto max-w-md font-mono text-xs"
        />
      )}
    </Card>
  );
};

/** Orchestre une partie : charge la session, affiche la manche courante, enchaine les manches. */
const GamePage: React.FC = () => {
  const { mode: slug } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const theme = useAppStore((state) => state.theme);
  const unlimited = searchParams.get('libre') === '1';

  const requestedMode = slugToMode(slug);
  const { modes, isLoading: modesLoading, error: modesError } = useGames(theme);
  const modeInfo = modes.find((mode) => mode.id === requestedMode) ?? null;

  // On attend la liste des modes : un mode absent de l'univers (Opening en manga) ne doit
  // pas lancer de partie.
  const { session, isLoading, error, notice, isBusy, isLoggedIn, restart, guess, guessCharacters, clue, skip } =
    useGameSession(modeInfo?.playable ? modeInfo.id : null, theme, unlimited);
  const { suggest } = useAutocomplete(theme);

  // Defi du jour : relu a chaque fin de manche pour tenir le total a jour.
  const closedRounds = session?.rounds.filter((candidate) => candidate.status !== 'IN_PROGRESS').length ?? 0;
  const { overview } = useDailyProgress(
    theme,
    session && !session.unlimited ? `${session.id}-${closedRounds}-${session.totalScore}` : undefined
  );

  // Manche affichee. Nulle : la premiere manche ouverte, ou le bilan si tout est joue.
  // Une action epingle sa manche, pour que le verdict reste visible jusqu'au clic « suivante ».
  const [pinned, setPinned] = useState<number | null>(null);
  const sessionId = session?.id;
  useEffect(() => {
    setPinned(null);
  }, [sessionId]);

  // Le mode vient d'etre valide mais la partie n'est pas encore demandee : c'est aussi un chargement.
  const awaitingSession = Boolean(modeInfo?.playable) && (isLoading || (!session && !error));
  if (modesLoading || awaitingSession) {
    return <p className="py-16 text-center font-display text-xl text-muted">Préparation de la planche…</p>;
  }

  if (modesError || !modeInfo || !modeInfo.playable || error || !session) {
    const message =
      modesError ??
      error ??
      (!modeInfo
        ? `Ce mode n'existe pas en ${theme}.`
        : "Le catalogue ne contient pas encore assez d'œuvres pour ce mode.");
    return (
      <Card className="mx-auto max-w-lg space-y-4 text-center">
        <p>{message}</p>
        <Link to="/" className="btn btn-ink">
          Retour au sommaire
        </Link>
      </Card>
    );
  }

  const firstOpen = session.rounds.find((round) => round.status === 'IN_PROGRESS')?.ordinal ?? null;
  const viewedOrdinal = pinned ?? firstOpen;
  const round: Round | null = session.rounds.find((candidate) => candidate.ordinal === viewedOrdinal) ?? null;

  const pinAnd = <T,>(ordinal: number, action: () => T): T => {
    setPinned(ordinal);
    return action();
  };

  const renderRound = (current: Round) => {
    if (session.mode === 'CHARACTERS') {
      return (
        <CharactersRound
          key={`${session.id}-${current.ordinal}`}
          round={current}
          theme={theme}
          busy={isBusy}
          titleSuggest={suggest}
          onSubmit={(entries) => pinAnd(current.ordinal, () => void guessCharacters(current.ordinal, entries))}
          onSkip={() => pinAnd(current.ordinal, () => void skip(current.ordinal))}
        />
      );
    }

    const props: RoundComponentProps = {
      round: current,
      theme,
      busy: isBusy,
      suggest,
      onGuess: (answer) => pinAnd(current.ordinal, () => guess(current.ordinal, answer)),
      onClue: () => pinAnd(current.ordinal, () => void clue(current.ordinal)),
      onSkip: () => {
        if (window.confirm('Abandonner cette manche ? Elle rapportera 0 point.')) {
          pinAnd(current.ordinal, () => void skip(current.ordinal));
        }
      },
    };
    const key = `${session.id}-${current.ordinal}`;

    switch (session.mode) {
      case 'IMAGES':
        return <ImageRound key={key} {...props} />;
      case 'OPENING':
      case 'ENDING':
        return <ThemeRound key={key} {...props} />;
      case 'COVER_REVEAL':
        return <CoverRevealRound key={key} {...props} />;
      case 'WORDLE':
        return <WordleRound key={key} {...props} />;
    }
  };

  const playableIds = modes.filter((mode) => mode.playable).map((mode) => mode.id);
  const next = overview ? nextDailyMode(overview, playableIds, session.mode) : null;
  const dailySummary =
    !session.unlimited && overview
      ? { overview, next, nextTitle: modes.find((mode) => mode.id === next?.mode)?.title ?? null }
      : null;

  const replay = () => {
    if (session.unlimited) {
      void restart();
    } else {
      navigate(`/play/${modeToSlug(session.mode)}?libre=1`);
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex items-start gap-4">
        <span
          aria-hidden="true"
          className="hidden h-16 w-16 flex-none items-center justify-center border-2 border-ink bg-accent font-display text-4xl text-on-accent shadow-hard sm:flex"
        >
          {MODE_MARK[session.mode]}
        </span>
        <div className="min-w-0 space-y-1">
          <p className="kicker">
            {session.unlimited
              ? 'Partie libre · hors classement'
              : `Défi n°${issueNumber(session.playDate)} · ${formatPlayDate(session.playDate)}`}
          </p>
          <h1 className="font-display text-3xl leading-tight sm:text-4xl">{modeInfo.title}</h1>
          <p className="text-sm text-muted">{modeInfo.description}</p>
          {!isLoggedIn && !session.unlimited && (
            <p className="text-xs text-muted">
              Partie anonyme :{' '}
              <Link to="/login" className="font-bold text-ink underline decoration-accent decoration-2 underline-offset-2">
                connectez-vous
              </Link>{' '}
              pour garder votre score et votre série.
            </p>
          )}
        </div>
      </header>

      {!session.unlimited && overview && (
        <DailyTrack overview={overview} modes={modes} theme={theme} current={session.mode} />
      )}

      <ScoreBoard session={session} theme={theme} viewedOrdinal={round ? round.ordinal : null} onSelect={setPinned} />

      {notice && (
        <p className="border-2 border-ink bg-near/30 px-4 py-2 text-sm font-medium">
          {notice}
        </p>
      )}

      {round ? (
        <Card className="space-y-4">
          {session.rounds.length > 1 && (
            <p className="kicker">
              Manche {round.ordinal + 1} / {session.rounds.length} · {DIFFICULTY_LABEL[round.difficulty]}
            </p>
          )}

          {renderRound(round)}

          {round.status !== 'IN_PROGRESS' && (
            <RoundResult
              round={round}
              mode={session.mode}
              theme={theme}
              hasNext={firstOpen !== null}
              onContinue={() => setPinned(null)}
            />
          )}
        </Card>
      ) : (
        <GameSummary
          session={session}
          theme={theme}
          modeTitle={modeInfo.title}
          onReplay={replay}
          daily={dailySummary}
        />
      )}
    </div>
  );
};

export default GamePage;
