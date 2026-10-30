import { ESTADOS_EQUIPO, ESTADOS_PRESTAMO } from '../util/formato.js';

/** Mensaje de error o de éxito. Con un ErrorApi muestra el detalle y los errores por campo de la API. */
export function Alerta({ tipo = 'error', error, children }) {
  if (!error && !children) {
    return null;
  }
  const campos = error?.errores ? Object.entries(error.errores) : [];
  return (
    <div className={`alerta alerta-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      {error ? error.message : children}
      {campos.length > 0 && (
        <ul>
          {campos.map(([campo, mensaje]) => (
            <li key={campo}>{mensaje}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function EstadoEquipo({ estado, activo = true }) {
  if (!activo) {
    return <span className="insignia insignia-inactivo">Desactivado</span>;
  }
  return <span className={`insignia insignia-${estado.toLowerCase()}`}>{ESTADOS_EQUIPO[estado]}</span>;
}

export function EstadoPrestamo({ estado }) {
  return <span className={`insignia insignia-${estado.toLowerCase()}`}>{ESTADOS_PRESTAMO[estado]}</span>;
}

export function Cargando() {
  return <p className="tenue">Cargando…</p>;
}

export function Vacio({ children }) {
  return <p className="vacio">{children}</p>;
}

export function Dialogo({ titulo, alCerrar, children }) {
  return (
    <div className="velo" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && alCerrar()}>
      <div className="dialogo" role="dialog" aria-modal="true" aria-label={titulo}>
        <div className="dialogo-cabeza">
          <h2>{titulo}</h2>
          <button type="button" className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Encabezado({ titulo, descripcion, children }) {
  return (
    <div className="encabezado">
      <div>
        <h1>{titulo}</h1>
        {descripcion && <p className="tenue">{descripcion}</p>}
      </div>
      {children && <div className="acciones">{children}</div>}
    </div>
  );
}
