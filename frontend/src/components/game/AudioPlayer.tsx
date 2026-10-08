import React, { useCallback, useEffect, useRef, useState } from 'react';

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds)) {
    return '0:00';
  }
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

/**
 * Lecteur du generique : un gros bouton lecture et une barre qu'on clique pour se deplacer.
 *
 * Le lecteur natif du navigateur change d'allure d'un systeme a l'autre et cache ses
 * commandes dans des icones minuscules ; c'est pourtant le coeur du mode. Espace lance ou
 * met en pause quand on n'est pas en train d'ecrire.
 */
const AudioPlayer: React.FC<{ src: string }> = ({ src }) => {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [failed, setFailed] = useState(false);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    if (audio.paused) {
      void audio.play();
    } else {
      audio.pause();
    }
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (e.code === 'Space' && !target?.closest('input, textarea, button')) {
        e.preventDefault();
        toggle();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [toggle]);

  const seek = (e: React.MouseEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !duration) {
      return;
    }
    const box = e.currentTarget.getBoundingClientRect();
    audio.currentTime = ((e.clientX - box.left) / box.width) * duration;
  };

  const progress = duration ? (time / duration) * 100 : 0;

  return (
    <div className="flex items-center gap-4">
      <audio
        ref={audioRef}
        src={src}
        preload="auto"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
        onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
        onLoadedMetadata={(e) => setDuration(e.currentTarget.duration)}
        // Le generique est relaye depuis AnimeThemes : si leur serveur ne repond pas, le proxy
        // renvoie 409 et le lecteur restait muet, bouton lecture compris, sans explication.
        onError={() => setFailed(true)}
      />
      {failed && (
        <p className="flex-1 border-2 border-dashed border-accent px-3 py-2 text-sm">
          Le générique ne se charge pas : sa source (AnimeThemes) ne répond pas. Passez la manche, ou réessayez plus
          tard.
        </p>
      )}
      {!failed && (
        <>
          <button
            type="button"
            onClick={toggle}
            aria-label={playing ? 'Pause' : 'Écouter le générique'}
            className="flex h-16 w-16 flex-none items-center justify-center rounded-full border-2 border-ink bg-accent text-2xl text-on-accent shadow-hard transition-transform hover:-translate-y-0.5 active:translate-y-0.5 active:shadow-none"
          >
            {playing ? '❚❚' : '▶'}
          </button>
          <div className="min-w-0 flex-1">
            <div
              role="slider"
              aria-label="Position dans le générique"
              aria-valuemin={0}
              aria-valuemax={Math.round(duration)}
              aria-valuenow={Math.round(time)}
              tabIndex={-1}
              onClick={seek}
              className="relative h-4 cursor-pointer border-2 border-ink bg-sheet"
            >
              <div className="screentone absolute inset-0" aria-hidden="true" />
              <div className="absolute inset-y-0 left-0 bg-ink" style={{ width: `${progress}%` }} />
            </div>
            <p className="mt-1 flex justify-between text-xs tabular-nums text-muted">
              <span>{formatTime(time)}</span>
              <span>{playing ? 'Espace : pause' : 'Espace : lecture'}</span>
              <span>{formatTime(duration)}</span>
            </p>
          </div>
        </>
      )}
    </div>
  );
};

export default AudioPlayer;
