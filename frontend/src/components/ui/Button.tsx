import React from 'react';

type Variant = 'primary' | 'secondary' | 'ghost';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  fullWidth?: boolean;
}

/** Classes des boutons : voir `.btn` dans index.css (bord d'encre, ombre franche). */
const VARIANTS: Record<Variant, string> = {
  primary: 'btn btn-ink',
  secondary: 'btn btn-paper',
  ghost: 'link-quiet px-2 py-1',
};

const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  fullWidth = false,
  className = '',
  children,
  ...props
}) => (
  <button className={[VARIANTS[variant], fullWidth ? 'w-full' : '', className].join(' ')} {...props}>
    {children}
  </button>
);

export default Button;
