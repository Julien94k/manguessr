import React, { useEffect, useState } from 'react';
import { formatCountdown } from '@/components/game/gameUi';

interface DailyCountdownProps {
  /** Secondes avant le prochain defi, telles que renvoyees par le serveur. */
  secondsUntilNext: number;
  /** Appele une fois le compte a rebours termine, pour recharger le defi du jour. */
  onElapsed?: () => void;
}

/**
 * Compte a rebours jusqu'au prochain defi (minuit UTC).
 *
 * Calcule depuis l'instant de reception plutot qu'en decrementant un compteur : un onglet en
 * arriere-plan voit ses minuteries ralenties, un decompte naif deriverait de plusieurs minutes.
 */
const DailyCountdown: React.FC<DailyCountdownProps> = ({ secondsUntilNext, onElapsed }) => {
  const [deadline] = useState(() => Date.now() + secondsUntilNext * 1000);
  const [remaining, setRemaining] = useState(secondsUntilNext);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      setRemaining(next);
      if (next === 0) {
        window.clearInterval(timer);
        onElapsed?.();
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [deadline, onElapsed]);

  return <span className="font-mono tabular-nums">{formatCountdown(remaining)}</span>;
};

export default DailyCountdown;
