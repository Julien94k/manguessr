/** @type {import('tailwindcss').Config} */

/** Couleur pilotee par une variable CSS (canaux RGB), pour garder l'opacite de Tailwind. */
const token = (name) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Zen Kaku Gothic New"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Dela Gothic One"', '"Zen Kaku Gothic New"', 'sans-serif'],
      },
      colors: {
        // Les couleurs du theme sont des jetons : leurs valeurs claires, sombres et par
        // univers vivent dans index.css. Une classe `bg-paper` suffit, sans variante `dark:`.
        paper: token('paper'),
        sheet: token('sheet'),
        ink: token('ink'),
        muted: token('muted'),
        accent: token('accent'),
        'on-accent': token('on-accent'),
        hit: token('hit'),
        miss: token('miss'),
        near: token('near'),
      },
      keyframes: {
        // Un tampon qu'on appose : arrive gros et de biais, se pose.
        stamp: {
          '0%': { opacity: '0', transform: 'scale(1.8) rotate(-14deg)' },
          '60%': { opacity: '1', transform: 'scale(0.94) rotate(-3deg)' },
          '100%': { transform: 'scale(1) rotate(-3deg)' },
        },
        // Mauvaise reponse : le champ secoue la tete.
        shake: {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%, 60%': { transform: 'translateX(-6px)' },
          '40%, 80%': { transform: 'translateX(6px)' },
        },
        rise: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        stamp: 'stamp 380ms cubic-bezier(.2,.8,.3,1.2) both',
        shake: 'shake 320ms ease-in-out',
        rise: 'rise 220ms ease-out both',
      },
      boxShadow: {
        // Ombre franche, decalee, sans flou : celle d'une case imprimee.
        hard: '4px 4px 0 0 rgb(var(--shade))',
        'hard-sm': '2px 2px 0 0 rgb(var(--shade))',
        'hard-lg': '7px 7px 0 0 rgb(var(--shade))',
      },
    },
  },
  plugins: [],
}
