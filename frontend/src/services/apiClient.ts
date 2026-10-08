import axios, { AxiosError } from 'axios';
import { useAppStore } from '@/store/useAppStore';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
const REQUEST_TIMEOUT_MS = 30_000;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Rattache le JWT a chaque requete sortante.
apiClient.interceptors.request.use((config) => {
  const token = useAppStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Normalise toutes les erreurs API en un message exploitable directement par les composants.
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ error?: string }>) => {
    // 401 : token expire ou invalide, on deconnecte.
    if (error.response?.status === 401) {
      useAppStore.getState().logout();
    }

    const serverMessage = error.response?.data?.error;
    error.message =
      serverMessage ??
      (error.code === 'ECONNABORTED' ? 'La requete a expire. Veuillez reessayer.' : null) ??
      error.message ??
      'Une erreur reseau inattendue s\'est produite.';

    return Promise.reject(error);
  }
);

export default apiClient;
