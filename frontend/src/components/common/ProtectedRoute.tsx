import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppStore } from '@/store/useAppStore';

/**
 * Garde les routes qui exigent un compte (profil, historique).
 * Le jeu lui-meme reste accessible sans connexion.
 */
const ProtectedRoute: React.FC = () => {
  const token = useAppStore((state) => state.token);
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
