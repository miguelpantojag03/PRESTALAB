import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente.js';
import {
  Alerta, Cargando, Dialogo, Encabezado, EstadoEquipo, EstadoPrestamo, Vacio,
} from '../componentes/Comunes.jsx';
import { CATEGORIAS, formatearFecha, hoyEnBogota } from '../util/formato.js';
import { useCarga } from '../util/useCarga.js';

/** Ejecuta una acción del administrador y muestra su resultado; tras el éxito recarga la lista. */
function useAccion(recargar) {
  const [mensaje, setMensaje] = useState(null);
  const [error, setError] = useState(null);
  const [ocupado, setOcupado] = useState(null);

  async function ejecutar(clave, accion, exito) {
    setOcupado(clave);
    setMensaje(null);
    setError(null);
    try {
      await accion();
      setMensaje(exito);
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

  return { mensaje, error, ocupado, ejecutar };
}

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
      <div className="tarjeta aviso">
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

/** RF05 y RF06: aprobar o rechazar solicitudes y registrar devoluciones. */
export function Prestamos() {
  const [pestana, setPestana] = useState('pendientes');
  const pendientes = useCarga(api.prestamosPendientes);
  const porDevolver = useCarga(api.prestamosPorDevolver);
  const recargarTodo = async () => {
    await Promise.all([pendientes.recargar(), porDevolver.recargar()]);
  };
  const accion = useAccion(recargarTodo);
  const [rechazando, setRechazando] = useState(null);
  const lista = pestana === 'pendientes' ? pendientes : porDevolver;
  const hoy = hoyEnBogota();

  return (
    <>
      <Encabezado titulo="Préstamos" descripcion="Aprueba o rechaza solicitudes y registra las devoluciones." />
      <div className="pestanas" role="tablist">
        <button type="button" role="tab" aria-selected={pestana === 'pendientes'}
                onClick={() => setPestana('pendientes')}>
          Pendientes {pendientes.datos && <span className="contador">{pendientes.datos.length}</span>}
        </button>
        <button type="button" role="tab" aria-selected={pestana === 'por-devolver'}
                onClick={() => setPestana('por-devolver')}>
          Por devolver {porDevolver.datos && <span className="contador">{porDevolver.datos.length}</span>}
        </button>
      </div>
      {accion.mensaje && <Alerta tipo="exito">{accion.mensaje}</Alerta>}
      <Alerta error={accion.error || lista.error} />
      {lista.cargando && !lista.datos ? (
        <Cargando />
      ) : lista.datos?.length === 0 ? (
        <Vacio>{pestana === 'pendientes' ? 'No hay solicitudes pendientes.' : 'No hay equipos por devolver.'}</Vacio>
      ) : (
        <div className="tabla-contenedor">
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
              {lista.datos?.map((p) => (
                <tr key={p.id} className={p.estado === 'APROBADO' && p.fechaDevolucionEsperada < hoy ? 'atrasado' : ''}>
                  <td>{p.id}</td>
                  <td>{p.nombreUsuario}<br /><small className="tenue">{p.correoUsuario}</small></td>
                  <td><span className="codigo">{p.codigoEquipo}</span> {p.nombreEquipo}</td>
                  <td>{formatearFecha(p.fechaSolicitud)}</td>
                  <td>{formatearFecha(p.fechaDevolucionEsperada)}</td>
                  <td><EstadoPrestamo estado={p.estado} /></td>
                  <td className="celda-acciones">
                    {pestana === 'pendientes' ? (
                      <>
                        <button type="button" className="boton" disabled={accion.ocupado !== null}
                                onClick={() => accion.ejecutar(p.id, () => api.aprobarPrestamo(p.id),
                                  `Aprobaste el préstamo #${p.id}. El equipo ${p.codigoEquipo} quedó PRESTADO.`)}>
                          Aprobar
                        </button>
                        <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                                onClick={() => setRechazando(p)}>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {rechazando && (
        <DialogoRechazo prestamo={rechazando} alCerrar={() => setRechazando(null)}
                        alRechazar={async (motivo) => {
                          const ok = await accion.ejecutar(rechazando.id,
                            () => api.rechazarPrestamo(rechazando.id, motivo),
                            `Rechazaste el préstamo #${rechazando.id}.`);
                          setRechazando(null);
                          return ok;
                        }} />
      )}
    </>
  );
}

function DialogoRechazo({ prestamo, alCerrar, alRechazar }) {
  const [motivo, setMotivo] = useState('');
  const [enviando, setEnviando] = useState(false);
  return (
    <Dialogo titulo={`Rechazar préstamo #${prestamo.id}`} alCerrar={alCerrar}>
      <form className="formulario" onSubmit={async (e) => {
        e.preventDefault();
        setEnviando(true);
        await alRechazar(motivo);
      }}>
        <p className="tenue">{prestamo.nombreUsuario} pidió {prestamo.nombreEquipo}. El motivo se le envía por correo.</p>
        <label>
          Motivo del rechazo
          <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} rows={3} required
                    autoFocus />
        </label>
        <div className="botones">
          <button type="button" className="boton boton-secundario" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton boton-peligro" disabled={enviando || !motivo.trim()}>
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
  const [editando, setEditando] = useState(null);
  const [desactivando, setDesactivando] = useState(null);

  return (
    <>
      <Encabezado titulo="Equipos" descripcion="Equipos activos del laboratorio.">
        <button type="button" className="boton" onClick={() => setEditando({})}>Nuevo equipo</button>
      </Encabezado>
      {accion.mensaje && <Alerta tipo="exito">{accion.mensaje}</Alerta>}
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
              {equipos?.map((eq) => (
                <tr key={eq.id}>
                  <td><span className="codigo">{eq.codigo}</span></td>
                  <td>{eq.nombre}{eq.descripcion && <><br /><small className="tenue">{eq.descripcion}</small></>}</td>
                  <td>{CATEGORIAS[eq.categoria]}</td>
                  <td><EstadoEquipo estado={eq.estado} /></td>
                  <td className="celda-acciones">
                    <button type="button" className="boton boton-secundario" onClick={() => setEditando(eq)}>
                      Editar
                    </button>
                    {eq.estado === 'DISPONIBLE' && (
                      <button type="button" className="boton boton-secundario" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(eq.id, () => api.cambiarEstadoEquipo(eq.id, 'EN_MANTENIMIENTO'),
                                `${eq.codigo} pasó a mantenimiento.`)}>
                        A mantenimiento
                      </button>
                    )}
                    {eq.estado === 'EN_MANTENIMIENTO' && (
                      <button type="button" className="boton boton-secundario" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(eq.id, () => api.cambiarEstadoEquipo(eq.id, 'DISPONIBLE'),
                                `${eq.codigo} volvió a estar disponible.`)}>
                        Marcar disponible
                      </button>
                    )}
                    {eq.estado !== 'PRESTADO' && (
                      <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                              onClick={() => setDesactivando(eq)}>
                        Desactivar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editando && (
        <DialogoEquipo equipo={editando} alCerrar={() => setEditando(null)}
                       alGuardar={async () => {
                         setEditando(null);
                         await recargar();
                       }} />
      )}
      {desactivando && (
        <Dialogo titulo={`Desactivar ${desactivando.codigo}`} alCerrar={() => setDesactivando(null)}>
          <p>
            {desactivando.nombre} dejará de aparecer en el catálogo. Su historial de préstamos se conserva y la acción
            no se puede deshacer desde la aplicación.
          </p>
          <div className="botones">
            <button type="button" className="boton boton-secundario" onClick={() => setDesactivando(null)}>
              Cancelar
            </button>
            <button type="button" className="boton boton-peligro" onClick={async () => {
              const eq = desactivando;
              setDesactivando(null);
              await accion.ejecutar(eq.id, () => api.desactivarEquipo(eq.id), `${eq.codigo} quedó desactivado.`);
            }}>
              Desactivar
            </button>
          </div>
        </Dialogo>
      )}
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
      if (nuevo) {
        await api.crearEquipo(datos);
      } else {
        await api.actualizarEquipo(equipo.id, datos);
      }
      await alGuardar();
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

/** RF12: activar y desactivar cuentas de estudiantes. */
export function Estudiantes() {
  const { datos: estudiantes, error, cargando, recargar } = useCarga(api.listarEstudiantes);
  const accion = useAccion(recargar);
  return (
    <>
      <Encabezado titulo="Estudiantes"
                  descripcion="Una cuenta desactivada no puede iniciar sesión ni pedir préstamos." />
      {accion.mensaje && <Alerta tipo="exito">{accion.mensaje}</Alerta>}
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
                  <td>{u.nombre}</td>
                  <td>{u.correo}</td>
                  <td>
                    <span className={`insignia ${u.activo ? 'insignia-disponible' : 'insignia-inactivo'}`}>
                      {u.activo ? 'Activa' : 'Desactivada'}
                    </span>
                  </td>
                  <td className="celda-acciones">
                    {u.activo ? (
                      <button type="button" className="boton boton-peligro" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(u.id, () => api.desactivarUsuario(u.id),
                                `Desactivaste la cuenta de ${u.nombre}.`)}>
                        Desactivar
                      </button>
                    ) : (
                      <button type="button" className="boton" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(u.id, () => api.activarUsuario(u.id),
                                `Reactivaste la cuenta de ${u.nombre}.`)}>
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
