import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Theme } from '@/types';

interface AppState {
  // Auth
  token: string | null;
  role: string | null;
  username: string | null;
  setAuth: (token: string, role: string, username: string) => void;
  logout: () => void;

  // Preferences
  theme: Theme;
  setTheme: (theme: Theme) => void;
  isDarkMode: boolean;
  toggleDarkMode: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      token: null,
      role: null,
      username: null,
      setAuth: (token, role, username) => set({ token, role, username }),
      logout: () => set({ token: null, role: null, username: null }),

      theme: 'anime',
      setTheme: (theme) => set({ theme }),
      isDarkMode: true,
      toggleDarkMode: () => set((state) => ({ isDarkMode: !state.isDarkMode })),
    }),
    {
      name: 'manguessr-storage',
      partialize: (state) => ({
        token: state.token,
        role: state.role,
        username: state.username,
        theme: state.theme,
        isDarkMode: state.isDarkMode,
      }),
    }
  )
);
