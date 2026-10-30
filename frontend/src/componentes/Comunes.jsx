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

/** Error de un formulario, junto a lo que lo causó: el detalle de la API y sus errores por campo. */
export function Alerta({ tipo = 'error', error, children }) {
  if (!error && !children) {
    return null;
  }
  const campos = error?.errores ? Object.entries(error.errores) : [];
  return (
    <motion.div key={error?.message ?? tipo} className={`alerta alerta-${tipo}`}
                role={tipo === 'error' ? 'alert' : 'status'}
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={RESORTE}>
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

const TONO_EQUIPO = { DISPONIBLE: 'pos', PRESTADO: 'dato', EN_MANTENIMIENTO: 'atencion' };
const TONO_PRESTAMO = { PENDIENTE: 'atencion', APROBADO: 'dato', VENCIDO: 'neg', DEVUELTO: '', RECHAZADO: '' };

/** Una sola marca de estado por fila: tinte pálido con tinta del mismo tono. */
export function EstadoEquipo({ estado, activo = true }) {
  if (!activo) {
    return <span className="pildora">Desactivado</span>;
  }
  return <span className={`pildora ${TONO_EQUIPO[estado]}`}>{ESTADOS_EQUIPO[estado]}</span>;
}

export function EstadoPrestamo({ estado }) {
  return <span className={`pildora ${TONO_PRESTAMO[estado]}`}>{ESTADOS_PRESTAMO[estado]}</span>;
}

/** Título de la pantalla: un sustantivo, la línea del hallazgo debajo y las acciones reales a la derecha. */
export function Titulo({ titulo, hallazgo, children }) {
  return (
    <div className="titulo">
      <div>
        <h1>{titulo}</h1>
        {hallazgo && <p className="hallazgo">{hallazgo}</p>}
      </div>
      {children && <div className="titulo-acciones">{children}</div>}
    </div>
  );
}

export function Vacio({ children }) {
  return <p className="vacio">{children}</p>;
}

/** Error dentro del bloque que falló: qué falló y un Reintentar. Nunca un aviso de pantalla completa. */
export function ErrorBloque({ que, error, alReintentar }) {
  return (
    <div className="error-bloque" role="alert">
      <span>No se pudo cargar {que}. {error?.codigo === 'SIN_CONEXION' ? 'No hay conexión con el servidor.' : ''}</span>
      <button type="button" className="boton fantasma" onClick={alReintentar}>Reintentar</button>
    </div>
  );
}

/** Cargando: la disposición final con barras grises del tamaño real; nada se mueve cuando llegan los datos. */
export function EsqueletoFilas({ filas = 4 }) {
  return (
    <div className="filas" aria-busy="true" aria-label="Cargando">
      {Array.from({ length: filas }, (_, i) => (
        <div key={i} className="fila">
          <div className="fila-principal">
            <span className="esqueleto" style={{ width: '46%' }} />
            <span className="esqueleto" style={{ width: '30%', marginTop: 8 }} />
          </div>
          <span className="esqueleto" style={{ width: 64 }} />
          <span />
        </div>
      ))}
    </div>
  );
}

/**
 * Tarjetas de conteo que filtran la lista de abajo (patrón "list" del estilo). Son pares: cada una hace trabajo.
 * Los conteos se calculan de las filas que hay en pantalla.
 */
export function Conteos({ etiqueta, opciones, valor, alCambiar }) {
  return (
    <div className="conteos" role="tablist" aria-label={etiqueta} style={{ '--opciones': opciones.length }}>
      {opciones.map((o) => (
        <button key={o.valor} type="button" role="tab" className="conteo" aria-selected={valor === o.valor}
                onClick={() => alCambiar(o.valor)}>
          <span className="conteo-etiqueta">{o.texto}</span>
          <span className="conteo-valor">{o.cuenta ?? '–'}</span>
        </button>
      ))}
    </div>
  );
}

/** Las filas que dejan la lista por una acción se van deslizando; las demás suben a su lugar. */
const salidaFila = {
  layout: 'position',
  initial: false,
  exit: { opacity: 0, x: 24 },
  transition: RESORTE,
};

/**
 * Tabla de escritorio que en el celular se vuelve una lista de dos líneas: lo que identifica a la fila y su cifra
 * clave en la primera, los demás campos en gris en la segunda, la acción debajo. Ningún campo se pierde.
 */
export function Tabla({ etiqueta, columnas, filas, clave, movil }) {
  const esCelular = useConsultaMedia('(max-width: 640px)');
  if (esCelular) {
    return (
      <ul className="lista-movil" aria-label={etiqueta}>
        <AnimatePresence initial={false}>
          {filas.map((fila) => {
            const m = movil(fila);
            return (
              <motion.li key={clave(fila)} {...salidaFila}>
                <span className="linea-1">{m.titulo}</span>
                <span className="valor">{m.valor}</span>
                <span className="linea-2">{m.detalle}</span>
                {m.acciones && <span className="acciones-movil">{m.acciones}</span>}
              </motion.li>
            );
          })}
        </AnimatePresence>
      </ul>
    );
  }
  return (
    <div className="tabla-marco">
      <table aria-label={etiqueta}>
        <thead>
          <tr>
            {columnas.map((c) => (
              <th key={c.titulo || 'acciones'} className={c.num ? 'n' : undefined} style={c.ancho ? { width: c.ancho } : undefined}>
                {c.titulo || <span className="sr">Acciones</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <AnimatePresence initial={false}>
            {filas.map((fila) => (
              <motion.tr key={clave(fila)} {...salidaFila}>
                {columnas.map((c) => (
                  <td key={c.titulo || 'acciones'} className={c.num ? 'n' : c.acciones ? 'acciones' : undefined}>
                    {c.celda(fila)}
                  </td>
                ))}
              </motion.tr>
            ))}
          </AnimatePresence>
        </tbody>
      </table>
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
 * - En escritorio nace del botón que lo abrió (transform-origin en ese botón) y se cierra volviendo a él.
 * - En el celular es una hoja inferior: se arrastra 1:1 desde su cabeza, resiste hacia arriba y, al soltarla, se
 *   proyecta el impulso: un gesto rápido hacia abajo la cierra aunque haya recorrido poco.
 * - Mientras sale queda inert: no recibe clics, foco ni texto, y no compite con un diálogo nuevo.
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
    : { initial: { opacity: 0, scale: 0.94 }, animate: { opacity: 1, scale: 1 }, exit: { opacity: 0, scale: 0.94 } };

  function alSoltar(_evento, info) {
    const alto = panel.current?.offsetHeight ?? 400;
    if (info.offset.y + proyectar(info.velocity.y) > alto * 0.5) {
      alCerrar();
    }
  }

  return (
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
 * Avisos de finalización: flotan (superficie opaca con sombra), se van solos y se descartan deslizándolos hacia un
 * lado. Pueden ofrecer una acción, por ejemplo Deshacer. En el celular aparecen arriba, porque abajo van las hojas.
 */
export function ProveedorAvisos({ children }) {
  const [avisos, setAvisos] = useState([]);
  const [salida, setSalida] = useState(0);
  const siguienteId = useRef(0);
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

// Entra desde el borde más cercano y, si se va solo, sale por ese mismo borde; si el dedo lo empujó, sale hacia allá.
const variantesAviso = {
  oculto: ({ arriba }) => ({ opacity: 0, y: arriba ? -16 : 16, x: 0 }),
  visible: { opacity: 1, y: 0, x: 0 },
  saliendo: ({ direccion, arriba }) =>
    (direccion ? { opacity: 0, x: direccion * 360 } : { opacity: 0, y: arriba ? -16 : 16 }),
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
                initial="oculto" animate="visible" exit="saliendo" transition={RESORTE_HOJA}
                drag="x" dragSnapToOrigin dragElastic={0.6} onDragEnd={alSoltar}
                onPointerEnter={() => setPausado(true)} onPointerLeave={() => setPausado(false)}
                onFocus={() => setPausado(true)} onBlur={() => setPausado(false)}>
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
