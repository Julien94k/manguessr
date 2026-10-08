import React, { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAppStore } from '@/store/useAppStore';
import Logo from '@/components/layout/Logo';
import { Theme } from '@/types';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-1 py-1 text-sm font-bold uppercase tracking-wide underline-offset-[6px] ${
    isActive ? 'underline decoration-accent decoration-[3px]' : 'text-muted hover:text-ink'
  }`;

const UNIVERSES: { id: Theme; label: string }[] = [
  { id: 'anime', label: 'Anime' },
  { id: 'manga', label: 'Manga' },
];

/**
 * Bascule d'univers : deux onglets d'intercalaire. L'onglet actif prend la couleur de
 * l'univers, puisque c'est lui qui la donne a tout le site.
 */
const UniverseTabs: React.FC<{ theme: Theme; onChange: (theme: Theme) => void }> = ({ theme, onChange }) => (
  <div role="radiogroup" aria-label="Univers" className="flex border-2 border-ink">
    {UNIVERSES.map((universe) => (
      <button
        key={universe.id}
        type="button"
        role="radio"
        aria-checked={theme === universe.id}
        onClick={() => onChange(universe.id)}
        className={`px-2 py-1 text-xs font-bold uppercase tracking-wide transition-colors sm:px-3 sm:text-sm ${
          theme === universe.id ? 'bg-accent text-on-accent' : 'bg-sheet text-muted hover:text-ink'
        }`}
      >
        {universe.label}
      </button>
    ))}
  </div>
);

const Navigation: React.FC = () => {
  const { username, theme, setTheme, isDarkMode, toggleDarkMode, logout } = useAppStore();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    setIsMenuOpen(false);
    navigate('/');
  };

  const darkToggle = (
    <button
      type="button"
      onClick={toggleDarkMode}
      aria-label={isDarkMode ? 'Passer en thème clair' : 'Passer en thème sombre'}
      className="h-9 w-9 border-2 border-ink bg-sheet text-base leading-none hover:bg-ink hover:text-paper"
    >
      {isDarkMode ? '☀' : '☾'}
    </button>
  );

  return (
    <header className="border-b-[3px] border-ink bg-paper">
      <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
        <Logo />

        <div className="hidden items-center gap-5 md:flex">
          <NavLink to="/" end className={navLinkClass}>
            Jouer
          </NavLink>
          <NavLink to="/leaderboard" className={navLinkClass}>
            Classement
          </NavLink>
          {username && (
            <NavLink to="/profile" className={navLinkClass}>
              {username}
            </NavLink>
          )}

          <UniverseTabs theme={theme} onChange={setTheme} />
          {darkToggle}

          {username ? (
            <button type="button" onClick={handleLogout} className="link-quiet">
              Déconnexion
            </button>
          ) : (
            <div className="flex items-center gap-3">
              <Link to="/login" className="link-quiet">
                Connexion
              </Link>
              <Link to="/register" className="btn btn-ink !py-1.5">
                Inscription
              </Link>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2 md:hidden">
          <UniverseTabs theme={theme} onChange={setTheme} />
          <button
            type="button"
            className="h-9 w-9 border-2 border-ink bg-sheet text-lg leading-none"
            aria-label="Ouvrir le menu"
            aria-expanded={isMenuOpen}
            onClick={() => setIsMenuOpen((open) => !open)}
          >
            {isMenuOpen ? '✕' : '☰'}
          </button>
        </div>
      </nav>

      {isMenuOpen && (
        <div className="flex flex-col gap-3 border-t-2 border-ink px-4 py-4 md:hidden">
          <NavLink to="/" end className={navLinkClass} onClick={() => setIsMenuOpen(false)}>
            Jouer
          </NavLink>
          <NavLink to="/leaderboard" className={navLinkClass} onClick={() => setIsMenuOpen(false)}>
            Classement
          </NavLink>
          {username && (
            <NavLink to="/profile" className={navLinkClass} onClick={() => setIsMenuOpen(false)}>
              Profil ({username})
            </NavLink>
          )}
          <div className="flex items-center gap-3 pt-1">
            {darkToggle}
            {username ? (
              <button type="button" onClick={handleLogout} className="link-quiet">
                Déconnexion
              </button>
            ) : (
              <>
                <Link to="/login" className="link-quiet" onClick={() => setIsMenuOpen(false)}>
                  Connexion
                </Link>
                <Link to="/register" className="btn btn-ink" onClick={() => setIsMenuOpen(false)}>
                  Inscription
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
};

export default Navigation;
