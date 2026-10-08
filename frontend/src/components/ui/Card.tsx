import React from 'react';

interface CardProps {
  className?: string;
  children: React.ReactNode;
}

/** Une case de planche : bord d'encre et ombre franche (`.panel`). */
const Card: React.FC<CardProps> = ({ className = '', children }) => (
  <div className={`panel p-6 ${className}`}>{children}</div>
);

export default Card;
