import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { api } from '../api/cliente.js';
import {
  Alerta, Cargando, Dialogo, Encabezado, EstadoEquipo, EstadoPrestamo, Vacio, useAvisos, useDialogo,
} from '../componentes/Comunes.jsx';
import { CATEGORIAS, formatearFecha, formatearFechaHora, hoyEnBogota, sumarDias } from '../util/formato.js';
import { RESORTE } from '../util/movimiento.js';
import { useCarga } from '../util/useCarga.js';

const DIAS_MAXIMOS = 7;

/** RF03 y RF04: catálogo con filtros y solicitud de préstamo. */
export function Catalogo() {
  const [categoria, setCategoria] = useState('');
  const [soloDisponibles, setSoloDisponibles] = useState(true);
  const consulta = useCallback(
    () => api.listarEquipos({ categoria, soloDisponibles }),
    [categoria, soloDisponibles],
  );
  const { datos: equipos, error, cargando, recargar } = useCarga(consulta);
  const solicitud = useDialogo();
  const { avisar } = useAvisos();
  const navegar = useNavigate();

  return (
    <>
      <Encabezado titulo="Catálogo de equipos"
                  descripcion="Elige un equipo disponible y pide el préstamo hasta por 7 días." />
      <div className="filtros">
        <label>
          Categoría
          <select value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            <option value="">Todas</option>
            {Object.entries(CATEGORIAS).map(([valor, texto]) => (
              <option key={valor} value={valor}>{texto}</option>
            ))}
          </select>
        </label>
        <label className="casilla">
          <input type="checkbox" checked={soloDisponibles} onChange={(e) => setSoloDisponibles(e.target.checked)} />
          Solo disponibles
        </label>
      </div>
      <Alerta error={error} />
      {cargando && !equipos ? (
        <Cargando />
      ) : equipos?.length === 0 ? (
        <Vacio>No hay equipos que coincidan con el filtro.</Vacio>
      ) : (
        // Al filtrar, las tarjetas que quedan se deslizan a su nuevo lugar y las demás se desvanecen en el suyo.
        <motion.div layout className="rejilla">
          <AnimatePresence initial={false} mode="popLayout">
            {equipos?.map((equipo) => (
              <motion.article key={equipo.id} layout className="tarjeta equipo" transition={RESORTE}
                              initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }}
                              exit={{ opacity: 0, scale: 0.96 }}>
                <div className="equipo-cabeza">
                  <span className="codigo">{equipo.codigo}</span>
                  <EstadoEquipo estado={equipo.estado} />
                </div>
                <h2>{equipo.nombre}</h2>
                <p className="tenue">{CATEGORIAS[equipo.categoria]}</p>
                {equipo.descripcion && <p>{equipo.descripcion}</p>}
                <button type="button" className="boton" disabled={equipo.estado !== 'DISPONIBLE'}
                        onClick={() => solicitud.abrir(equipo)}>
                  Solicitar préstamo
                </button>
              </motion.article>
            ))}
          </AnimatePresence>
        </motion.div>
      )}
      <AnimatePresence>
        {solicitud.dato && (
          <DialogoSolicitud key={solicitud.clave} equipo={solicitud.dato} alCerrar={solicitud.cerrar}
                            alSolicitar={(prestamo) => {
                              solicitud.cerrar();
                              avisar({
                                texto: `Solicitaste ${prestamo.nombreEquipo}. Quedó PENDIENTE de aprobación.`,
                                accion: { texto: 'Ver mis préstamos', alHacer: () => navegar('/mis-prestamos') },
                              });
                              recargar();
                            }} />
        )}
      </AnimatePresence>
    </>
  );
}

function DialogoSolicitud({ equipo, alCerrar, alSolicitar }) {
  const hoy = hoyEnBogota();
  const limite = sumarDias(hoy, DIAS_MAXIMOS);
  const [fecha, setFecha] = useState(sumarDias(hoy, 3));
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const fueraDeRango = fecha < hoy || fecha > limite;

  async function enviar(evento) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      alSolicitar(await api.solicitarPrestamo({ equipoId: equipo.id, fechaDevolucionEsperada: fecha }));
    } catch (e) {
      setError(e);
      setEnviando(false);
    }
  }

  return (
    <Dialogo titulo={`Solicitar ${equipo.nombre}`} alCerrar={alCerrar}>
      <form onSubmit={enviar} className="formulario">
        <Alerta error={error} />
        <div className="campo">
          <label htmlFor="fecha-devolucion">Fecha de devolución</label>
          <input id="fecha-devolucion" type="date" value={fecha} min={hoy} max={limite}
                 onChange={(e) => setFecha(e.target.value)} required aria-invalid={fueraDeRango}
                 aria-describedby="ayuda-fecha" />
          <small id="ayuda-fecha" className={`ayuda ${fueraDeRango ? 'ayuda-invalido' : 'ayuda-tenue'}`}>
            Entre hoy y el {formatearFecha(limite)} (máximo 7 días).
          </small>
        </div>
        <div className="botones">
          <button type="button" className="boton boton-secundario" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton" disabled={enviando || fueraDeRango}>
            {enviando ? 'Enviando…' : 'Confirmar solicitud'}
          </button>
        </div>
      </form>
    </Dialogo>
  );
}

/**
 * La API ordena por fecha de solicitud; dos préstamos del mismo día se desempatan aquí por número,
 * el más nuevo primero.
 */
const historialOrdenado = async () =>
  (await api.misPrestamos()).sort((a, b) => b.fechaSolicitud.localeCompare(a.fechaSolicitud) || b.id - a.id);

/** RF07: historial del estudiante. */
export function MisPrestamos() {
  const { datos: prestamos, error, cargando } = useCarga(historialOrdenado);
  return (
    <>
      <Encabezado titulo="Mis préstamos" descripcion="Tus solicitudes, de la más reciente a la más antigua." />
      <Alerta error={error} />
      {cargando && !prestamos ? (
        <Cargando />
      ) : prestamos?.length === 0 ? (
        <Vacio>
          Aún no has pedido préstamos. <Link to="/catalogo">Ve al catálogo</Link>.
        </Vacio>
      ) : (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Equipo</th>
                <th>Solicitado</th>
                <th>Devolver el</th>
                <th>Estado</th>
                <th>Detalle</th>
              </tr>
            </thead>
            <tbody>
              {prestamos?.map((p) => (
                <tr key={p.id}>
                  <td className="numero" data-etiqueta="Préstamo">{p.id}</td>
                  <td data-etiqueta="Equipo"><span className="codigo">{p.codigoEquipo}</span> {p.nombreEquipo}</td>
                  <td data-etiqueta="Solicitado">{formatearFecha(p.fechaSolicitud)}</td>
                  <td data-etiqueta="Devolver el">{formatearFecha(p.fechaDevolucionEsperada)}</td>
                  <td data-etiqueta="Estado"><EstadoPrestamo estado={p.estado} /></td>
                  <td className="tenue" data-etiqueta="Detalle">{detallePrestamo(p)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function detallePrestamo(p) {
  switch (p.estado) {
    case 'APROBADO':
      return `Aprobado el ${formatearFechaHora(p.fechaAprobacion)}`;
    case 'RECHAZADO':
      return `Motivo: ${p.motivoRechazo}`;
    case 'DEVUELTO':
      return `Devuelto el ${formatearFecha(p.fechaDevolucionReal)}`;
    case 'VENCIDO':
      return 'Pasó la fecha de devolución: devuélvelo cuanto antes';
    default:
      return 'Esperando aprobación';
  }
}
