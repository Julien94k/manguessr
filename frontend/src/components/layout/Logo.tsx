import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Titre du site, compose comme le logo d'un magazine : le nom en capitales grasses, et un
 * cartouche d'accent qui porte l'univers en cours.
 */
const Logo: React.FC<{ size?: 'md' | 'lg'; className?: string }> = ({ size = 'md', className = '' }) => (
  <Link to="/" className={`group inline-flex items-baseline gap-0.5 font-display leading-none ${className}`}>
    <span className={size === 'lg' ? 'text-4xl' : 'text-xl sm:text-2xl'}>MAN</span>
    <span
      className={`bg-accent px-1 text-on-accent transition-transform group-hover:-rotate-2 ${
        size === 'lg' ? 'text-4xl' : 'text-xl sm:text-2xl'
      }`}
    >
      GUESSR
    </span>
  </Link>
);

export default Logo;
