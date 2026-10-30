// Un solo juego de íconos de contorno, 16px y trazo de 1.5, en el color del texto que los rodea.
const base = {
  width: 16, height: 16, viewBox: '0 0 16 16', fill: 'none', stroke: 'currentColor',
  strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true,
};

export const IconoPanel = () => (
  <svg {...base}><rect x="2" y="2" width="5" height="5" rx="1" /><rect x="9" y="2" width="5" height="5" rx="1" /><rect x="2" y="9" width="5" height="5" rx="1" /><rect x="9" y="9" width="5" height="5" rx="1" /></svg>
);

export const IconoPrestamos = () => (
  <svg {...base}><path d="M3 5h9.5M10 2.5 12.5 5 10 7.5" /><path d="M13 11H3.5M6 8.5 3.5 11 6 13.5" /></svg>
);

export const IconoEquipos = () => (
  <svg {...base}><path d="M2.5 5 8 2.5 13.5 5v6L8 13.5 2.5 11z" /><path d="M2.5 5 8 7.5 13.5 5M8 7.5v6" /></svg>
);

export const IconoEstudiantes = () => (
  <svg {...base}><circle cx="6" cy="5.5" r="2.5" /><path d="M1.5 13.5c.6-2.3 2.3-3.5 4.5-3.5s3.9 1.2 4.5 3.5" /><path d="M10.5 3.2a2.4 2.4 0 0 1 0 4.6M12 10.3c1.2.5 2 1.6 2.5 3.2" /></svg>
);

export const IconoCatalogo = () => (
  <svg {...base}><rect x="2" y="2.5" width="12" height="11" rx="1.5" /><path d="M2 6.5h12M6 6.5v7" /></svg>
);

export const IconoHistorial = () => (
  <svg {...base}><circle cx="8" cy="8" r="5.5" /><path d="M8 5v3l2 1.5" /></svg>
);

export const IconoSalir = () => (
  <svg {...base}><path d="M6 13.5H3.5a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1H6M10.5 11 13.5 8l-3-3M13.5 8H6" /></svg>
);

export const IconoMenu = () => (
  <svg {...base}><path d="M2.5 4.5h11M2.5 8h11M2.5 11.5h11" /></svg>
);

export const IconoActualizar = () => (
  <svg {...base}><path d="M13.5 8a5.5 5.5 0 1 1-1.6-3.9M13.5 2.5v3h-3" /></svg>
);

/** Marca de PrestaLab: un matraz de laboratorio sobre el tono de datos. */
export function Marca({ tamano = 28 }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 28 28" aria-hidden="true">
      <rect width="28" height="28" rx="7" fill="var(--dato)" />
      <path d="M11.5 6.5h5M12.5 6.5v5.2L8 19.6a1.6 1.6 0 0 0 1.4 2.4h9.2a1.6 1.6 0 0 0 1.4-2.4l-4.5-7.9V6.5"
            fill="none" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 17.5h8" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

// Tonos de identidad para las personas: pálidos, separados al menos 60 grados, fuera de los gráficos.
const TONOS = [16, 136, 196, 256, 76, 316];

/** Inicial estable de una persona sobre un tinte pálido de su propio tono. */
export function Avatar({ nombre, id }) {
  const semilla = id ?? [...nombre].reduce((suma, letra) => suma + letra.charCodeAt(0), 0);
  const tono = TONOS[Math.abs(semilla) % TONOS.length];
  return (
    <span className="avatar" style={{ background: `hsl(${tono} 60% 88%)` }} aria-hidden="true">
      {nombre.trim().charAt(0).toUpperCase()}
    </span>
  );
}
