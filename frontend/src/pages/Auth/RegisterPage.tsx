import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '@/services/api';
import { useAppStore } from '@/store/useAppStore';
import Button from '@/components/ui/Button';
import TextField from '@/components/ui/TextField';
import AuthLayout from '@/pages/Auth/AuthLayout';

const RegisterPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
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
      // Le backend renvoie deja un JWT : pas de verification email a franchir.
      const data = await register({ email, username, password });
      setAuth(data.token, data.role, data.username);
      navigate('/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Inscription impossible.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Créer un compte"
      subtitle={
        <>
          Déjà inscrit ?{' '}
          <Link to="/login" className="font-bold text-ink underline decoration-accent decoration-2 underline-offset-4">
            Connectez-vous
          </Link>
        </>
      }
    >
      <form className="space-y-5" onSubmit={handleSubmit}>
        <TextField
          label="Pseudo"
          name="username"
          required
          minLength={3}
          maxLength={24}
          autoComplete="nickname"
          placeholder="Affiché dans le classement"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
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
          minLength={8}
          autoComplete="new-password"
          placeholder="8 caractères minimum"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && (
          <p className="border-2 border-accent p-3 text-center text-sm font-bold text-accent">
            {error}
          </p>
        )}

        <Button type="submit" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Création...' : 'Créer mon compte'}
        </Button>
      </form>
    </AuthLayout>
  );
};

export default RegisterPage;
