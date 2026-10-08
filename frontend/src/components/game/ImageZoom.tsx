import React, { useEffect, useState } from 'react';

/**
 * Image agrandissable : un clic l'ouvre en plein ecran, un clic ou Echap la referme.
 *
 * Les pages de manga sont denses et les vignettes d'episodes petites : sans loupe, on devinait
 * a la taille de la colonne. Rien n'est revele de plus — c'est le meme fichier, deja traite
 * (floute, recadre) par le serveur.
 */
const ImageZoom: React.FC<{ src: string; alt: string; className?: string; style?: React.CSSProperties }> = ({
  src,
  alt,
  className = '',
  style,
}) => {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const close = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [open]);

  return (
    <>
      <img
        src={src}
        alt={alt}
        title="Cliquer pour agrandir"
        onClick={() => setOpen(true)}
        className={`cursor-zoom-in ${className}`}
        style={style}
      />
      {open && (
        <div
          role="dialog"
          aria-label={alt}
          onClick={() => setOpen(false)}
          className="fixed inset-0 z-50 flex cursor-zoom-out items-center justify-center bg-[#0c0a08]/90 p-4"
        >
          <img src={src} alt={alt} className="max-h-full max-w-full animate-rise border-2 border-paper object-contain" />
          <p className="absolute bottom-3 left-0 right-0 text-center text-xs text-[#d8d0c0]">Clic ou Échap pour fermer</p>
        </div>
      )}
    </>
  );
};

export default ImageZoom;
