import { useEffect } from 'react';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import { useAppStore } from '@/store/useAppStore';
import Navigation from '@/components/layout/Navigation';
import ProtectedRoute from '@/components/common/ProtectedRoute';
import Home from '@/pages/Home';
import GamePage from '@/pages/GamePage';
import LeaderboardPage from '@/pages/LeaderboardPage';
import ProfilePage from '@/pages/ProfilePage';
import LoginPage from '@/pages/Auth/LoginPage';
import RegisterPage from '@/pages/Auth/RegisterPage';

function App() {
  const isDarkMode = useAppStore((state) => state.isDarkMode);
  const theme = useAppStore((state) => state.theme);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDarkMode);
  }, [isDarkMode]);

  // L'univers choisit la couleur d'accent (variables CSS de index.css).
  useEffect(() => {
    document.documentElement.dataset.universe = theme;
  }, [theme]);

  return (
    <Router>
      <Routes>
        {/* Pages d'authentification : plein écran, sans navigation. */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route
          path="*"
          element={
            <div className="flex min-h-screen flex-col">
              <Navigation />
              <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
                <Routes>
                  {/* Jouable sans compte. */}
                  <Route path="/" element={<Home />} />
                  <Route path="/play/:mode" element={<GamePage />} />
                  <Route path="/leaderboard" element={<LeaderboardPage />} />

                  {/* Compte requis. */}
                  <Route element={<ProtectedRoute />}>
                    <Route path="/profile" element={<ProfilePage />} />
                  </Route>
                </Routes>
              </main>
              <footer className="border-t-[3px] border-ink">
                <div className="mx-auto flex max-w-6xl flex-wrap justify-between gap-2 px-4 py-5 text-xs text-muted sm:px-6 lg:px-8">
                  <p>ManGuessr — imprimé chaque nuit sur un Raspberry Pi.</p>
                  <p>Données AniList, MangaDex et AnimeThemes.</p>
                </div>
              </footer>
            </div>
          }
        />
      </Routes>
    </Router>
  );
}

export default App;
