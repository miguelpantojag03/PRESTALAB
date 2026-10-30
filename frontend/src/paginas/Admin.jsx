import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { api } from '../api/cliente.js';
import {
  Alerta, Cargando, Dialogo, Encabezado, EstadoEquipo, EstadoPrestamo, Vacio, useAvisos, useDialogo,
} from '../componentes/Comunes.jsx';
import { CATEGORIAS, formatearFecha, hoyEnBogota } from '../util/formato.js';
import { RESORTE, RESORTE_RAPIDO } from '../util/movimiento.js';
import { useCarga } from '../util/useCarga.js';

/**
 * Ejecuta una acción del administrador. El éxito se confirma con un aviso; si la acción es reversible, el aviso
 * ofrece Deshacer en lugar de pedir confirmación antes (la confirmación queda para lo irreversible).
 * El error se muestra en línea, sobre la lista que lo causó.
 */
function useAccion(recargar) {
  const { avisar } = useAvisos();
  const [error, setError] = useState(null);
  const [ocupado, setOcupado] = useState(null);

  async function ejecutar(clave, accion, exito, deshacer) {
    setOcupado(clave);
    setError(null);
    try {
      await accion();
      avisar({
        texto: exito,
        accion: deshacer && {
          texto: 'Deshacer',
          alHacer: async () => {
            try {
              await deshacer();
              avisar('Se deshizo el cambio.');
            } catch (e) {
              setError(e);
            }
            await recargar();
          },
        },
      });
      await recargar();
      return true;
    } catch (e) {
      setError(e);
      // Un 409 o 422 suele significar que otra persona cambió el dato: se recarga para mostrar lo actual.
      await recargar();
      return false;
    } finally {
      setOcupado(null);
    }
  }

  return { error, ocupado, ejecutar };
}

/** Las filas que dejan la lista (aprobada, rechazada, devuelta) se van deslizando; las demás suben a su lugar. */
const filaAnimada = {
  layout: 'position',
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0, x: 32 },
  transition: RESORTE,
};

/** RF10: resumen del panel. */
export function Panel() {
  const { datos: resumen, error } = useCarga(api.resumen);
  const { datos: pendientes } = useCarga(api.prestamosPendientes);
  const tarjetas = [
    { clave: 'disponibles', texto: 'Disponibles', clase: 'disponible' },
    { clave: 'prestados', texto: 'Prestados', clase: 'prestado' },
    { clave: 'enMantenimiento', texto: 'En mantenimiento', clase: 'en_mantenimiento' },
    { clave: 'vencidos', texto: 'Préstamos vencidos', clase: 'vencido' },
  ];
  return (
    <>
      <Encabezado titulo="Panel del laboratorio" descripcion="Estado actual de los equipos y los préstamos." />
      <Alerta error={error} />
      <div className="indicadores">
        {tarjetas.map((t) => (
          <div key={t.clave} className={`indicador indicador-${t.clase}`}>
            <span>{t.texto}</span>
            <strong>{resumen ? resumen[t.clave] : '–'}</strong>
          </div>
        ))}
      </div>
      <div className="tarjeta aviso-panel">
        <div>
          <h2>Solicitudes pendientes</h2>
          <p className="tenue">
            {pendientes == null
              ? 'Consultando…'
              : pendientes.length === 0
                ? 'No hay solicitudes esperando aprobación.'
                : `${pendientes.length} solicitud(es) esperan tu aprobación.`}
          </p>
        </div>
        <Link to="/admin/prestamos" className="boton">Revisar préstamos</Link>
      </div>
    </>
  );
}

function Pestanas({ valor, alCambiar, opciones }) {
  return (
    <div className="pestanas" role="tablist">
      {opciones.map((o) => (
        <button key={o.valor} type="button" role="tab" aria-selected={valor === o.valor}
                onClick={() => alCambiar(o.valor)}>
          {o.texto}
          {o.cuenta != null && <span className="contador">{o.cuenta}</span>}
          {valor === o.valor && (
            <motion.span layoutId="pestana-activa" className="pestana-indicador" transition={RESORTE_RAPIDO} />
          )}
        </button>
      ))}
    </div>
  );
}

/** RF05 y RF06: aprobar o rechazar solicitudes y registrar devoluciones. */
export function Prestamos() {
  const [pestana, setPestana] = useState('pendientes');
  const pendientes = useCarga(api.prestamosPendientes);
  const porDevolver = useCarga(api.prestamosPorDevolver);
  const recargarTodo = async () => {
    await Promise.all([pendientes.recargar(), porDevolver.recargar()]);
  };
  const accion = useAccion(recargarTodo);
  const rechazo = useDialogo();
  const lista = pestana === 'pendientes' ? pendientes : porDevolver;
  const hoy = hoyEnBogota();

  return (
    <>
      <Encabezado titulo="Préstamos" descripcion="Aprueba o rechaza solicitudes y registra las devoluciones." />
      <Pestanas valor={pestana} alCambiar={setPestana} opciones={[
        { valor: 'pendientes', texto: 'Pendientes', cuenta: pendientes.datos?.length },
        { valor: 'por-devolver', texto: 'Por devolver', cuenta: porDevolver.datos?.length },
      ]} />
      <Alerta error={accion.error || lista.error} />
      {lista.cargando && !lista.datos ? (
        <Cargando />
      ) : lista.datos?.length === 0 ? (
        <Vacio>{pestana === 'pendientes' ? 'No hay solicitudes pendientes.' : 'No hay equipos por devolver.'}</Vacio>
      ) : (
        // La tabla se monta de nuevo al cambiar de pestaña: solo se animan las filas que salen por una acción.
        <div className="tabla-contenedor" key={pestana}>
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Estudiante</th>
                <th>Equipo</th>
                <th>Solicitado</th>
                <th>Devolver el</th>
                <th>Estado</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              <AnimatePresence initial={false}>
                {lista.datos?.map((p) => (
                  <motion.tr key={p.id} {...filaAnimada}
                             className={p.estado === 'APROBADO' && p.fechaDevolucionEsperada < hoy ? 'atrasado' : ''}>
                    <td className="numero" data-etiqueta="Préstamo">{p.id}</td>
                    <td data-etiqueta="Estudiante">
                      <span>{p.nombreUsuario}<br /><small className="tenue">{p.correoUsuario}</small></span>
                    </td>
                    <td data-etiqueta="Equipo"><span><span className="codigo">{p.codigoEquipo}</span> {p.nombreEquipo}</span></td>
                    <td data-etiqueta="Solicitado">{formatearFecha(p.fechaSolicitud)}</td>
                    <td data-etiqueta="Devolver el">{formatearFecha(p.fechaDevolucionEsperada)}</td>
                    <td data-etiqueta="Estado"><EstadoPrestamo estado={p.estado} /></td>
                    <td className="celda-acciones">
                      {pestana === 'pendientes' ? (
                        <>
                          <button type="button" className="boton" disabled={accion.ocupado !== null}
                                  onClick={() => accion.ejecutar(p.id, () => api.aprobarPrestamo(p.id),
                                    `Aprobaste el préstamo #${p.id}. El equipo ${p.codigoEquipo} quedó PRESTADO.`)}>
                            Aprobar
                          </button>
                          <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                                  onClick={() => rechazo.abrir(p)}>
                            Rechazar
                          </button>
                        </>
                      ) : (
                        <button type="button" className="boton" disabled={accion.ocupado !== null}
                                onClick={() => accion.ejecutar(p.id, () => api.devolverPrestamo(p.id),
                                  `Registraste la devolución del préstamo #${p.id}.`)}>
                          Registrar devolución
                        </button>
                      )}
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </div>
      )}
      <AnimatePresence>
        {rechazo.dato && (
          <DialogoRechazo key={rechazo.clave} prestamo={rechazo.dato} alCerrar={rechazo.cerrar}
                          alRechazar={async (motivo) => {
                            const p = rechazo.dato;
                            rechazo.cerrar();
                            await accion.ejecutar(p.id, () => api.rechazarPrestamo(p.id, motivo),
                              `Rechazaste el préstamo #${p.id}.`);
                          }} />
        )}
      </AnimatePresence>
    </>
  );
}

function DialogoRechazo({ prestamo, alCerrar, alRechazar }) {
  const [motivo, setMotivo] = useState('');
  return (
    <Dialogo titulo={`Rechazar préstamo #${prestamo.id}`} alCerrar={alCerrar}>
      <form className="formulario" onSubmit={(e) => {
        e.preventDefault();
        alRechazar(motivo);
      }}>
        <p className="tenue">{prestamo.nombreUsuario} pidió {prestamo.nombreEquipo}. El motivo se le envía por correo.</p>
        <div className="campo">
          <label htmlFor="motivo-rechazo">Motivo del rechazo</label>
          <textarea id="motivo-rechazo" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500}
                    rows={3} required autoFocus aria-describedby="ayuda-motivo" />
          <small id="ayuda-motivo" className="ayuda ayuda-tenue">{motivo.length}/500</small>
        </div>
        <div className="botones">
          <button type="button" className="boton boton-secundario" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton boton-peligro" disabled={!motivo.trim()}>
            Rechazar
          </button>
        </div>
      </form>
    </Dialogo>
  );
}

/** RF02 y RN07: gestión de equipos. Un equipo nunca se elimina, solo se desactiva. */
export function Equipos() {
  const { datos: equipos, error, cargando, recargar } = useCarga(api.listarEquipos);
  const accion = useAccion(recargar);
  const { avisar } = useAvisos();
  const edicion = useDialogo();
  const desactivacion = useDialogo();

  const cambiarEstado = (eq, nuevo, texto) => accion.ejecutar(eq.id,
    () => api.cambiarEstadoEquipo(eq.id, nuevo), texto,
    () => api.cambiarEstadoEquipo(eq.id, eq.estado));

  return (
    <>
      <Encabezado titulo="Equipos" descripcion="Equipos activos del laboratorio.">
        <button type="button" className="boton" onClick={() => edicion.abrir({})}>Nuevo equipo</button>
      </Encabezado>
      <Alerta error={accion.error || error} />
      {cargando && !equipos ? (
        <Cargando />
      ) : equipos?.length === 0 ? (
        <Vacio>No hay equipos activos.</Vacio>
      ) : (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Categoría</th>
                <th>Estado</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              <AnimatePresence initial={false}>
                {equipos?.map((eq) => (
                  <motion.tr key={eq.id} {...filaAnimada}>
                    <td data-etiqueta="Código"><span className="codigo">{eq.codigo}</span></td>
                    <td data-etiqueta="Nombre">
                      <span>{eq.nombre}{eq.descripcion && <><br /><small className="tenue">{eq.descripcion}</small></>}</span>
                    </td>
                    <td data-etiqueta="Categoría">{CATEGORIAS[eq.categoria]}</td>
                    <td data-etiqueta="Estado"><EstadoEquipo estado={eq.estado} /></td>
                    <td className="celda-acciones">
                      <button type="button" className="boton boton-secundario" onClick={() => edicion.abrir(eq)}>
                        Editar
                      </button>
                      {eq.estado === 'DISPONIBLE' && (
                        <button type="button" className="boton boton-secundario" disabled={accion.ocupado !== null}
                                onClick={() => cambiarEstado(eq, 'EN_MANTENIMIENTO', `${eq.codigo} pasó a mantenimiento.`)}>
                          A mantenimiento
                        </button>
                      )}
                      {eq.estado === 'EN_MANTENIMIENTO' && (
                        <button type="button" className="boton boton-secundario" disabled={accion.ocupado !== null}
                                onClick={() => cambiarEstado(eq, 'DISPONIBLE', `${eq.codigo} volvió a estar disponible.`)}>
                          Marcar disponible
                        </button>
                      )}
                      {eq.estado !== 'PRESTADO' && (
                        <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                                onClick={() => desactivacion.abrir(eq)}>
                          Desactivar
                        </button>
                      )}
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </div>
      )}
      <AnimatePresence>
        {edicion.dato && (
          <DialogoEquipo key={edicion.clave} equipo={edicion.dato} alCerrar={edicion.cerrar}
                         alGuardar={async (guardado, nuevo) => {
                           edicion.cerrar();
                           avisar(nuevo ? `Creaste el equipo ${guardado.codigo}.` : `Guardaste ${guardado.codigo}.`);
                           await recargar();
                         }} />
        )}
      </AnimatePresence>
      <AnimatePresence>
        {desactivacion.dato && (
          // Desactivar no se puede deshacer desde la aplicación: es el único caso que pide confirmación.
          <Dialogo key={desactivacion.clave} titulo={`Desactivar ${desactivacion.dato.codigo}`}
                   alCerrar={desactivacion.cerrar}>
            <p>
              {desactivacion.dato.nombre} dejará de aparecer en el catálogo. Su historial de préstamos se conserva y
              la acción no se puede deshacer desde la aplicación.
            </p>
            <div className="botones">
              <button type="button" className="boton boton-secundario" onClick={desactivacion.cerrar}>
                Cancelar
              </button>
              <button type="button" className="boton boton-peligro" onClick={async () => {
                const eq = desactivacion.dato;
                desactivacion.cerrar();
                await accion.ejecutar(eq.id, () => api.desactivarEquipo(eq.id), `${eq.codigo} quedó desactivado.`);
              }}>
                Desactivar
              </button>
            </div>
          </Dialogo>
        )}
      </AnimatePresence>
    </>
  );
}

function DialogoEquipo({ equipo, alCerrar, alGuardar }) {
  const nuevo = !equipo.id;
  const [datos, setDatos] = useState({
    codigo: equipo.codigo ?? '',
    nombre: equipo.nombre ?? '',
    descripcion: equipo.descripcion ?? '',
    categoria: equipo.categoria ?? 'PORTATIL',
  });
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const cambiar = (campo) => (e) => setDatos({ ...datos, [campo]: e.target.value });

  async function enviar(evento) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const guardado = nuevo ? await api.crearEquipo(datos) : await api.actualizarEquipo(equipo.id, datos);
      await alGuardar(guardado, nuevo);
    } catch (e) {
      setError(e);
      setEnviando(false);
    }
  }

  return (
    <Dialogo titulo={nuevo ? 'Nuevo equipo' : `Editar ${equipo.codigo}`} alCerrar={alCerrar}>
      <form onSubmit={enviar} className="formulario">
        <Alerta error={error} />
        <div className="dos-columnas">
          <label>
            Código
            {/* La API solo admite mayúsculas, números y guiones: se convierte mientras se escribe. */}
            <input value={datos.codigo} maxLength={30} pattern="[A-Z0-9\-]+" required autoFocus
                   onChange={(e) => setDatos({ ...datos, codigo: e.target.value.toUpperCase().replace(/\s/g, '-') })}
                   title="Solo letras mayúsculas, números y guiones" />
          </label>
          <label>
            Categoría
            <select value={datos.categoria} onChange={cambiar('categoria')}>
              {Object.entries(CATEGORIAS).map(([valor, texto]) => (
                <option key={valor} value={valor}>{texto}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Nombre
          <input value={datos.nombre} onChange={cambiar('nombre')} maxLength={100} required />
        </label>
        <label>
          <span>Descripción <span className="tenue">(opcional)</span></span>
          <textarea value={datos.descripcion} onChange={cambiar('descripcion')} maxLength={500} rows={3} />
        </label>
        <div className="botones">
          <button type="button" className="boton boton-secundario" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton" disabled={enviando}>
            {enviando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Dialogo>
  );
}

/** RF12: activar y desactivar cuentas de estudiantes. Es reversible, así que se ofrece Deshacer. */
export function Estudiantes() {
  const { datos: estudiantes, error, cargando, recargar } = useCarga(api.listarEstudiantes);
  const accion = useAccion(recargar);
  return (
    <>
      <Encabezado titulo="Estudiantes"
                  descripcion="Una cuenta desactivada no puede iniciar sesión ni pedir préstamos." />
      <Alerta error={accion.error || error} />
      {cargando && !estudiantes ? (
        <Cargando />
      ) : estudiantes?.length === 0 ? (
        <Vacio>Aún no hay estudiantes registrados.</Vacio>
      ) : (
        <div className="tabla-contenedor">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Correo</th>
                <th>Estado</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {estudiantes?.map((u) => (
                <tr key={u.id}>
                  <td data-etiqueta="Nombre">{u.nombre}</td>
                  <td data-etiqueta="Correo">{u.correo}</td>
                  <td data-etiqueta="Estado">
                    <span className={`insignia ${u.activo ? 'insignia-disponible' : 'insignia-inactivo'}`}>
                      {u.activo ? 'Activa' : 'Desactivada'}
                    </span>
                  </td>
                  <td className="celda-acciones">
                    {u.activo ? (
                      <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(u.id, () => api.desactivarUsuario(u.id),
                                `Desactivaste la cuenta de ${u.nombre}.`, () => api.activarUsuario(u.id))}>
                        Desactivar
                      </button>
                    ) : (
                      <button type="button" className="boton" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(u.id, () => api.activarUsuario(u.id),
                                `Reactivaste la cuenta de ${u.nombre}.`, () => api.desactivarUsuario(u.id))}>
                        Activar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
