import React from 'react';
import Card from '@/components/ui/Card';
import Logo from '@/components/layout/Logo';

interface AuthLayoutProps {
  title: string;
  subtitle: React.ReactNode;
  children: React.ReactNode;
}

/** Coquille commune aux pages de connexion et d'inscription, affichée hors navigation. */
const AuthLayout: React.FC<AuthLayoutProps> = ({ title, subtitle, children }) => (
  <div className="speedlines flex min-h-screen items-center justify-center px-4 py-12">
    <div className="w-full max-w-md space-y-8">
      <div className="text-center">
        <Logo size="lg" />
      </div>

      <Card className="p-8 shadow-hard-lg">
        <h1 className="font-display text-3xl">{title}</h1>
        <p className="mb-6 mt-2 text-sm text-muted">{subtitle}</p>
        {children}
      </Card>
    </div>
  </div>
);

export default AuthLayout;
