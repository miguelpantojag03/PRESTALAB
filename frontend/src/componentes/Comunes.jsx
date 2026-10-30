import {
  createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState,
} from 'react';
import { AnimatePresence, motion, useDragControls, useIsPresent } from 'motion/react';
import { ESTADOS_EQUIPO, ESTADOS_PRESTAMO } from '../util/formato.js';
import { RESORTE, RESORTE_HOJA, proyectar } from '../util/movimiento.js';

export function useConsultaMedia(consulta) {
  const leer = () => (typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(consulta).matches : false);
  const [coincide, setCoincide] = useState(leer);
  useEffect(() => {
    if (!window.matchMedia) {
      return undefined;
    }
    const media = window.matchMedia(consulta);
    const cambiar = () => setCoincide(media.matches);
    media.addEventListener('change', cambiar);
    return () => media.removeEventListener('change', cambiar);
  }, [consulta]);
  return coincide;
}

/**
 * Error o advertencia en línea, junto a lo que lo causó. Con un ErrorApi muestra el detalle y los errores por
 * campo de la API. Se materializa (opacidad y desplazamiento corto) en lugar de aparecer de golpe.
 */
export function Alerta({ tipo = 'error', error, children }) {
  if (!error && !children) {
    return null;
  }
  const campos = error?.errores ? Object.entries(error.errores) : [];
  return (
    <motion.div key={error?.message ?? tipo} className={`alerta alerta-${tipo}`}
                role={tipo === 'error' ? 'alert' : 'status'}
                initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} transition={RESORTE}>
      {error ? error.message : children}
      {campos.length > 0 && (
        <ul>
          {campos.map(([campo, mensaje]) => (
            <li key={campo}>{mensaje}</li>
          ))}
        </ul>
      )}
    </motion.div>
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
  return <p className="tenue cargando">Cargando…</p>;
}

export function Vacio({ children }) {
  return <p className="vacio">{children}</p>;
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

/**
 * Estado de un diálogo con una clave nueva en cada apertura. Si se reabre mientras el anterior todavía se está
 * yendo, el nuevo es otra instancia con su propio estado; con una clave fija, AnimatePresence revive la que salía
 * con los datos viejos (por ejemplo, "Guardando…").
 */
export function useDialogo() {
  const [abierto, setAbierto] = useState(null);
  const aperturas = useRef(0);
  const abrir = useCallback((dato) => setAbierto({ dato, clave: ++aperturas.current }), []);
  const cerrar = useCallback(() => setAbierto(null), []);
  return { dato: abierto?.dato ?? null, clave: abierto?.clave, abrir, cerrar };
}

/**
 * Diálogo para una tarea modal. Quien lo use lo envuelve en <AnimatePresence> para que la salida también se anime.
 *
 * - En escritorio nace del botón que lo abrió (transform-origin en ese botón) y se cierra volviendo a él: entra y
 *   sale por el mismo camino.
 * - En el celular es una hoja inferior. Se arrastra desde la barra superior siguiendo al dedo 1:1 y opone
 *   resistencia elástica hacia arriba. Al soltarla se proyecta el impulso: un movimiento rápido hacia abajo la
 *   cierra aunque haya recorrido poco.
 * - Un velo oscurece el fondo porque la tarea es modal. Mientras sale ya no bloquea clics.
 */
export function Dialogo({ titulo, alCerrar, children }) {
  const esCelular = useConsultaMedia('(max-width: 640px)');
  const presente = useIsPresent();
  const panel = useRef(null);
  const disparador = useRef(typeof document !== 'undefined' ? document.activeElement : null);
  const controlesArrastre = useDragControls();

  useLayoutEffect(() => {
    const boton = disparador.current;
    const elemento = panel.current;
    if (esCelular || !elemento || !boton?.getBoundingClientRect || boton === document.body) {
      return;
    }
    const origen = boton.getBoundingClientRect();
    // offsetLeft/offsetTop no incluyen la escala inicial de la animación, así que el origen queda exacto.
    const x = origen.left + origen.width / 2 - elemento.offsetLeft;
    const y = origen.top + origen.height / 2 - elemento.offsetTop;
    elemento.style.transformOrigin = `${x}px ${y}px`;
  }, [esCelular]);

  useEffect(() => {
    const alTeclear = (e) => e.key === 'Escape' && alCerrar();
    document.addEventListener('keydown', alTeclear);
    const boton = disparador.current;
    return () => {
      document.removeEventListener('keydown', alTeclear);
      if (boton?.isConnected && boton.focus) {
        boton.focus({ preventScroll: true });
      }
    };
  }, [alCerrar]);

  const movimiento = esCelular
    ? { initial: { y: '100%' }, animate: { y: 0 }, exit: { y: '100%' } }
    : { initial: { opacity: 0, scale: 0.9 }, animate: { opacity: 1, scale: 1 }, exit: { opacity: 0, scale: 0.9 } };

  function alSoltar(_evento, info) {
    const alto = panel.current?.offsetHeight ?? 400;
    const destino = info.offset.y + proyectar(info.velocity.y);
    if (destino > alto * 0.5) {
      alCerrar();
    }
  }

  return (
    // Mientras sale queda inert: no recibe clics, foco ni texto, y no compite con un diálogo nuevo.
    <motion.div className="velo" role="presentation" inert={presente ? undefined : ''}
                style={{ pointerEvents: presente ? 'auto' : 'none' }}
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={RESORTE}
                onPointerDown={(e) => e.target === e.currentTarget && alCerrar()}>
      <motion.div ref={panel} className={esCelular ? 'dialogo hoja' : 'dialogo'} role="dialog" aria-modal="true"
                  aria-label={titulo} {...movimiento} transition={RESORTE}
                  drag={esCelular ? 'y' : false} dragListener={false} dragControls={controlesArrastre}
                  dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0.08, bottom: 1 }}
                  dragTransition={{ bounceStiffness: 420, bounceDamping: 34 }} onDragEnd={alSoltar}>
        <div className="dialogo-cabeza" onPointerDown={(e) => esCelular && controlesArrastre.start(e)}>
          {esCelular && <span className="agarradera" aria-hidden="true" />}
          <h2>{titulo}</h2>
          <button type="button" className="cerrar" onClick={alCerrar} aria-label="Cerrar">×</button>
        </div>
        {children}
      </motion.div>
    </motion.div>
  );
}

const ContextoAvisos = createContext(null);
const DURACION_AVISO_MS = 5000;

/**
 * Avisos de finalización. Aparecen abajo en un material translúcido, se van solos y se pueden descartar
 * deslizándolos hacia un lado (con proyección de impulso). Pueden ofrecer una acción, por ejemplo Deshacer.
 */
export function ProveedorAvisos({ children }) {
  const [avisos, setAvisos] = useState([]);
  const [salida, setSalida] = useState(0);
  const siguienteId = useRef(0);
  // En el celular la parte de abajo es de las hojas, así que los avisos aparecen arriba.
  const arriba = useConsultaMedia('(max-width: 640px)');

  const quitar = useCallback((id, direccion = 0) => {
    setSalida(direccion);
    setAvisos((lista) => lista.filter((a) => a.id !== id));
  }, []);

  const avisar = useCallback((aviso) => {
    const id = ++siguienteId.current;
    const nuevo = typeof aviso === 'string' ? { texto: aviso } : aviso;
    setAvisos((lista) => [...lista.slice(-2), { ...nuevo, id }]);
    return id;
  }, []);

  const valor = useMemo(() => ({ avisar }), [avisar]);

  return (
    <ContextoAvisos.Provider value={valor}>
      {children}
      <div className={arriba ? 'avisos arriba' : 'avisos'} role="status" aria-live="polite">
        <AnimatePresence initial={false} custom={{ direccion: salida, arriba }}>
          {avisos.map((aviso) => (
            <Aviso key={aviso.id} aviso={aviso} alQuitar={quitar} arriba={arriba} />
          ))}
        </AnimatePresence>
      </div>
    </ContextoAvisos.Provider>
  );
}

// Entra desde el borde más cercano (abajo, o arriba en el celular) y, si se va solo, sale por ese mismo borde.
// Si el dedo lo empujó, sale hacia donde lo empujó.
const variantesAviso = {
  oculto: ({ arriba }) => ({ opacity: 0, y: arriba ? -24 : 24, x: 0 }),
  visible: { opacity: 1, y: 0, x: 0 },
  saliendo: ({ direccion, arriba }) =>
    (direccion ? { opacity: 0, x: direccion * 360 } : { opacity: 0, y: arriba ? -24 : 24 }),
};

function Aviso({ aviso, alQuitar, arriba }) {
  const [pausado, setPausado] = useState(false);

  useEffect(() => {
    if (pausado) {
      return undefined;
    }
    const temporizador = setTimeout(() => alQuitar(aviso.id), DURACION_AVISO_MS);
    return () => clearTimeout(temporizador);
  }, [aviso.id, alQuitar, pausado]);

  function alSoltar(_evento, info) {
    const destino = info.offset.x + proyectar(info.velocity.x);
    if (Math.abs(destino) > 140) {
      alQuitar(aviso.id, Math.sign(destino));
    }
  }

  return (
    <motion.div layout className="aviso" variants={variantesAviso} custom={{ direccion: 0, arriba }}
                initial="oculto" animate="visible"
                exit="saliendo" transition={RESORTE_HOJA} drag="x" dragSnapToOrigin dragElastic={0.6}
                onDragEnd={alSoltar} onPointerEnter={() => setPausado(true)}
                onPointerLeave={() => setPausado(false)} onFocus={() => setPausado(true)}
                onBlur={() => setPausado(false)}>
      <span>{aviso.texto}</span>
      {aviso.accion && (
        <button type="button" className="aviso-accion" onClick={() => {
          alQuitar(aviso.id);
          aviso.accion.alHacer();
        }}>
          {aviso.accion.texto}
        </button>
      )}
    </motion.div>
  );
}

export function useAvisos() {
  return useContext(ContextoAvisos);
}
