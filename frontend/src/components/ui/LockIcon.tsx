import React from 'react';

/** Cadenas en SVG : l'emoji ne s'affiche pas sur les systemes sans police emoji. */
const LockIcon: React.FC<{ className?: string }> = ({ className = 'h-3.5 w-3.5' }) => (
  <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" className={`inline-block flex-none ${className}`}>
    <path
      fillRule="evenodd"
      d="M10 1a4.5 4.5 0 0 0-4.5 4.5V9H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-.5V5.5A4.5 4.5 0 0 0 10 1Zm3 8V5.5a3 3 0 1 0-6 0V9h6Z"
      clipRule="evenodd"
    />
  </svg>
);

export default LockIcon;
