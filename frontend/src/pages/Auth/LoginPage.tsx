import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login } from '@/services/api';
import { useAppStore } from '@/store/useAppStore';
import Button from '@/components/ui/Button';
import TextField from '@/components/ui/TextField';
import AuthLayout from '@/pages/Auth/AuthLayout';

const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const navigate = useNavigate();
  const setAuth = useAppStore((state) => state.setAuth);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const data = await login({ email, password });
      setAuth(data.token, data.role, data.username);
      navigate('/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Connexion impossible.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Connexion"
      subtitle={
        <>
          Pas encore de compte ?{' '}
          <Link to="/register" className="font-bold text-ink underline decoration-accent decoration-2 underline-offset-4">
            Inscrivez-vous
          </Link>
        </>
      }
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <TextField
          label="Email"
          name="email"
          type="email"
          required
          autoComplete="email"
          placeholder="vous@exemple.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label="Mot de passe"
          name="password"
          type="password"
          required
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && (
          <p className="border-2 border-accent p-3 text-center text-sm font-bold text-accent">
            {error}
          </p>
        )}

        <Button type="submit" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Connexion...' : 'Se connecter'}
        </Button>
      </form>
    </AuthLayout>
  );
};

export default LoginPage;
