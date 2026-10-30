import { useCallback, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';
import { api } from '../api/cliente.js';
import {
  Alerta, Conteos, Dialogo, ErrorBloque, EsqueletoFilas, EstadoEquipo, EstadoPrestamo, Tabla, Titulo, Vacio,
  useAvisos, useDialogo,
} from '../componentes/Comunes.jsx';
import { Avatar, IconoActualizar } from '../componentes/Iconos.jsx';
import { useCola } from '../componentes/Plantilla.jsx';
import {
  CATEGORIAS, CATEGORIAS_PLURAL, ESTADOS_EQUIPO, cuandoDevolver, fechaCorta, formatearFecha, hoyEnBogota,
} from '../util/formato.js';
import { useCarga } from '../util/useCarga.js';

const FILAS_EN_PANEL = 5;

/**
 * Ejecuta una acción del administrador. El éxito se confirma con un aviso; si la acción es reversible, el aviso
 * ofrece Deshacer en lugar de pedir confirmación antes (la confirmación queda para lo irreversible).
 * El error se muestra en línea, sobre la lista que lo causó. Después de cada acción se recalcula la cola del menú.
 */
function useAccion(recargar) {
  const { avisar } = useAvisos();
  const { recargarCola } = useCola();
  const [error, setError] = useState(null);
  const [ocupado, setOcupado] = useState(null);

  const recargarTodo = async () => {
    await Promise.all([recargar(), recargarCola()]);
  };

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
            await recargarTodo();
          },
        },
      });
      await recargarTodo();
      return true;
    } catch (e) {
      setError(e);
      // Un 409 o 422 suele significar que otra persona cambió el dato: se recarga para mostrar lo actual.
      await recargarTodo();
      return false;
    } finally {
      setOcupado(null);
    }
  }

  return { error, ocupado, ejecutar };
}

/** Datos del laboratorio que comparten el panel y las listas: una sola consulta por recurso. */
function useLaboratorio() {
  const consulta = useCallback(async () => {
    const [equipos, pendientes, porDevolver, resumen] = await Promise.all([
      api.listarEquipos(), api.prestamosPendientes(), api.prestamosPorDevolver(), api.resumen(),
    ]);
    return { equipos, pendientes, porDevolver, resumen, consultado: new Date() };
  }, []);
  return useCarga(consulta);
}

const esVencido = (p, hoy) => p.estado === 'VENCIDO' || p.fechaDevolucionEsperada < hoy;

function DevolverEl({ fecha, hoy }) {
  const cuando = cuandoDevolver(fecha, hoy);
  return (
    <>
      <span className={cuando.dias < 0 ? 'atrasado' : undefined}>{cuando.texto}</span>
      <span className="sub">{fechaCorta(fecha)}</span>
    </>
  );
}

/** Pregunta: "¿Qué hay que atender hoy en el mostrador?". Lo más grande es la cola de solicitudes. */
export function Panel() {
  const { datos, error, cargando, recargar } = useLaboratorio();
  const accion = useAccion(recargar);
  const rechazo = useDialogo();
  const [estadoMarcado, setEstadoMarcado] = useState('PRESTADO');
  const hoy = hoyEnBogota();

  const equipos = datos?.equipos ?? [];
  const pendientes = datos?.pendientes ?? [];
  const porDevolver = [...(datos?.porDevolver ?? [])].sort((a, b) =>
    a.fechaDevolucionEsperada.localeCompare(b.fechaDevolucionEsperada));
  const vencidas = porDevolver.filter((p) => esVencido(p, hoy)).length;
  const vencenHoy = porDevolver.filter((p) => p.fechaDevolucionEsperada === hoy).length;
  const prestamoDe = Object.fromEntries(porDevolver.map((p) => [p.equipoId, p]));

  // Las cifras del estante salen de la misma lista que lo dibuja: una cifra mostrada dos veces es una variable.
  const cuenta = (estado) => equipos.filter((e) => e.estado === estado).length;
  const indicadores = [
    { valor: 'DISPONIBLE', texto: 'Disponibles' },
    { valor: 'PRESTADO', texto: 'Prestados' },
    { valor: 'EN_MANTENIMIENTO', texto: 'En mantenimiento' },
  ];

  const hallazgo = !datos ? null : pendientes.length === 0 && vencidas === 0 && vencenHoy === 0
    ? 'Todo al día: no hay solicitudes por aprobar ni devoluciones atrasadas.'
    : [
        pendientes.length > 0 && <strong key="p">{pendientes.length} {pendientes.length === 1 ? 'solicitud espera' : 'solicitudes esperan'} tu aprobación</strong>,
        vencidas > 0 && <span key="v" className="atrasado">{vencidas} {vencidas === 1 ? 'devolución atrasada' : 'devoluciones atrasadas'}</span>,
        vencenHoy > 0 && <span key="h">{vencenHoy} {vencenHoy === 1 ? 'equipo vuelve' : 'equipos vuelven'} hoy</span>,
      ].filter(Boolean).flatMap((parte, i) => (i ? [' · ', parte] : [parte]));

  return (
    <>
      <Titulo titulo="Panel del laboratorio" hallazgo={hallazgo}>
        {datos && (
          <span className="sello">
            Actualizado a las {datos.consultado.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}
          </span>
        )}
        <button type="button" className="boton" onClick={recargar} disabled={cargando}>
          <IconoActualizar /> Actualizar
        </button>
      </Titulo>
      <Alerta error={accion.error} />

      <div className="reticula">
        <section className="panel columna ocupa-7" aria-labelledby="t-solicitudes">
          <header>
            <h2 id="t-solicitudes">Solicitudes por aprobar{datos && <span className="num">{pendientes.length}</span>}</h2>
          </header>
          {error ? <ErrorBloque que="las solicitudes" error={error} alReintentar={recargar} />
            : !datos ? <EsqueletoFilas />
            : pendientes.length === 0 ? <Vacio>No hay solicitudes esperando aprobación.</Vacio>
            : (
              <div className="filas">
                {pendientes.slice(0, FILAS_EN_PANEL).map((p) => (
                  <div key={p.id} className="fila">
                    <div className="fila-principal">
                      <strong>{p.nombreEquipo}</strong> <span className="mono tenue">{p.codigoEquipo}</span>
                      <span className="sub">{p.nombreUsuario} · hasta {fechaCorta(p.fechaDevolucionEsperada)}</span>
                    </div>
                    <div className="fila-valor">
                      <span className="sub">Pedido el {fechaCorta(p.fechaSolicitud)}</span>
                    </div>
                    <div className="fila-acciones">
                      <button type="button" className="boton" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(p.id, () => api.aprobarPrestamo(p.id),
                                `Aprobaste el préstamo #${p.id}. El equipo ${p.codigoEquipo} quedó PRESTADO.`)}>
                        Aprobar
                      </button>
                      <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                              onClick={() => rechazo.abrir(p)}>
                        Rechazar
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          {datos && pendientes.length > 0 && (
            <div className="panel-pie">
              <span>{pendientes.length > FILAS_EN_PANEL ? `Mostrando ${FILAS_EN_PANEL} de ${pendientes.length}` : 'La más antigua primero'}</span>
              <Link to="/admin/prestamos" className="enlace">Ver todas las solicitudes</Link>
            </div>
          )}
        </section>

        <section className="panel columna ocupa-5" aria-labelledby="t-devoluciones">
          <header>
            <h2 id="t-devoluciones">Devoluciones{datos && <span className="num">{porDevolver.length}</span>}</h2>
          </header>
          {error ? <ErrorBloque que="las devoluciones" error={error} alReintentar={recargar} />
            : !datos ? <EsqueletoFilas />
            : porDevolver.length === 0 ? <Vacio>No hay equipos afuera en este momento.</Vacio>
            : (
              <div className="filas">
                {porDevolver.slice(0, FILAS_EN_PANEL).map((p) => (
                  <div key={p.id} className="fila">
                    <div className="fila-principal">
                      <strong>{p.nombreEquipo}</strong>
                      <span className="sub">{p.nombreUsuario}</span>
                    </div>
                    <div className="fila-valor"><DevolverEl fecha={p.fechaDevolucionEsperada} hoy={hoy} /></div>
                    <div className="fila-acciones">
                      <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                              onClick={() => accion.ejecutar(p.id, () => api.devolverPrestamo(p.id),
                                `Recibiste ${p.nombreEquipo}. Volvió a estar disponible.`)}>
                        Recibir
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          {datos && porDevolver.length > 0 && (
            <div className="panel-pie">
              <span className={vencidas > 0 ? 'atrasado' : undefined}>
                {vencidas > 0 ? `${vencidas} ${vencidas === 1 ? 'atrasada' : 'atrasadas'}` : 'Ninguna atrasada'}
              </span>
              <Link to="/admin/prestamos?ver=por-devolver" className="enlace">Ver todas las devoluciones</Link>
            </div>
          )}
        </section>

        <section className="panel ocupa-12" aria-labelledby="t-estante">
          <header>
            <h2 id="t-estante">El estante</h2>
            <span className="sello">
              {datos ? `${equipos.length} equipos activos · ${datos.resumen.vencidos} ${datos.resumen.vencidos === 1 ? 'préstamo vencido' : 'préstamos vencidos'}` : ''}
            </span>
          </header>
          {/* Los indicadores son las pestañas del estante: el estado elegido toma el tono de datos. */}
          <div className="kpis" role="tablist" aria-label="Equipos por estado">
            {indicadores.map((k) => (
              <button key={k.valor} type="button" role="tab" className="kpi" aria-selected={estadoMarcado === k.valor}
                      onClick={() => setEstadoMarcado(k.valor)}>
                <span className="kpi-etiqueta">{k.texto}</span>
                <span className="kpi-valor">{datos ? cuenta(k.valor) : '–'}</span>
              </button>
            ))}
          </div>
          {error ? <ErrorBloque que="los equipos" error={error} alReintentar={recargar} />
            : !datos ? <EsqueletoFilas filas={2} />
            : <Estante equipos={equipos} marcado={estadoMarcado} prestamoDe={prestamoDe} />}
        </section>
      </div>

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

/**
 * El elemento propio de PrestaLab: cada equipo activo es una celda con su código, agrupada por categoría.
 * La celda del estado elegido toma el único tono de datos; el título de cada celda dice quién lo tiene.
 */
function Estante({ equipos, marcado, prestamoDe }) {
  const grupos = Object.keys(CATEGORIAS)
    .map((categoria) => ({ categoria, lista: equipos.filter((e) => e.categoria === categoria) }))
    .filter((g) => g.lista.length > 0);
  return (
    <>
      <div className="estante">
        {grupos.map((g) => (
          <div key={g.categoria} className="estante-grupo">
            <h3>{CATEGORIAS_PLURAL[g.categoria]}<span className="num">{g.lista.length}</span></h3>
            <div className="estante-celdas">
              {g.lista.map((e) => {
                const p = prestamoDe[e.id];
                const detalle = p ? `${e.nombre} · con ${p.nombreUsuario} hasta el ${fechaCorta(p.fechaDevolucionEsperada)}`
                  : `${e.nombre} · ${ESTADOS_EQUIPO[e.estado]}`;
                return (
                  <span key={e.id} className={e.estado === marcado ? 'celda marcada' : 'celda'} title={detalle}
                        aria-label={detalle}>
                    {e.codigo}
                  </span>
                );
              })}
            </div>
          </div>
        ))}
      </div>
      <div className="estante-leyenda" aria-hidden="true">
        <span><i className="muestra marcada" /> {ESTADOS_EQUIPO[marcado]}</span>
        <span><i className="muestra" /> Los demás</span>
      </div>
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
          <textarea id="motivo-rechazo" className="entrada" value={motivo} onChange={(e) => setMotivo(e.target.value)}
                    maxLength={500} rows={3} required autoFocus aria-describedby="ayuda-motivo" />
          <small id="ayuda-motivo" className="ayuda">{motivo.length}/500</small>
        </div>
        <div className="botones">
          <button type="button" className="boton" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton primario" disabled={!motivo.trim()}>Rechazar</button>
        </div>
      </form>
    </Dialogo>
  );
}

/** Pregunta: "¿Qué préstamo necesita una acción?". Las tarjetas de conteo filtran la lista. */
export function Prestamos() {
  const { datos, error, recargar } = useLaboratorio();
  const accion = useAccion(recargar);
  const rechazo = useDialogo();
  const [parametros] = useSearchParams();
  const [vista, setVista] = useState(parametros.get('ver') === 'por-devolver' ? 'por-devolver' : 'pendientes');
  const hoy = hoyEnBogota();

  const porDevolver = useMemo(() => [...(datos?.porDevolver ?? [])].sort((a, b) =>
    a.fechaDevolucionEsperada.localeCompare(b.fechaDevolucionEsperada)), [datos]);
  const vistas = {
    pendientes: datos?.pendientes ?? [],
    'por-devolver': porDevolver,
    vencidos: porDevolver.filter((p) => esVencido(p, hoy)),
  };
  const filas = vistas[vista];

  const columnas = [
    { titulo: 'Préstamo', ancho: 96, celda: (p) => <span className="mono">#{p.id}</span> },
    { titulo: 'Estudiante', celda: (p) => <>{p.nombreUsuario}<span className="sub">{p.correoUsuario}</span></> },
    { titulo: 'Equipo', celda: (p) => <>{p.nombreEquipo}<span className="sub mono">{p.codigoEquipo}</span></> },
    { titulo: 'Solicitado', num: true, celda: (p) => formatearFecha(p.fechaSolicitud) },
    { titulo: 'Devolver', num: true, celda: (p) => <DevolverEl fecha={p.fechaDevolucionEsperada} hoy={hoy} /> },
    { titulo: 'Estado', celda: (p) => <EstadoPrestamo estado={p.estado} /> },
    { titulo: '', acciones: true, celda: (p) => accionesPrestamo(p) },
  ];

  function accionesPrestamo(p) {
    if (p.estado === 'PENDIENTE') {
      return (
        <>
          <button type="button" className="boton" disabled={accion.ocupado !== null}
                  onClick={() => accion.ejecutar(p.id, () => api.aprobarPrestamo(p.id),
                    `Aprobaste el préstamo #${p.id}. El equipo ${p.codigoEquipo} quedó PRESTADO.`)}>
            Aprobar
          </button>
          <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                  onClick={() => rechazo.abrir(p)}>
            Rechazar
          </button>
        </>
      );
    }
    return (
      <button type="button" className="boton" disabled={accion.ocupado !== null}
              onClick={() => accion.ejecutar(p.id, () => api.devolverPrestamo(p.id),
                `Registraste la devolución del préstamo #${p.id}.`)}>
        Registrar devolución
      </button>
    );
  }

  const vacios = {
    pendientes: 'No hay solicitudes esperando aprobación.',
    'por-devolver': 'No hay equipos afuera en este momento.',
    vencidos: 'Ningún préstamo está atrasado.',
  };

  return (
    <>
      <Titulo titulo="Préstamos" hallazgo="Aprueba o rechaza las solicitudes y registra lo que vuelve al laboratorio." />
      <Conteos etiqueta="Préstamos por estado" valor={vista} alCambiar={setVista} opciones={[
        { valor: 'pendientes', texto: 'Por aprobar', cuenta: datos && vistas.pendientes.length },
        { valor: 'por-devolver', texto: 'Por devolver', cuenta: datos && vistas['por-devolver'].length },
        { valor: 'vencidos', texto: 'Atrasados', cuenta: datos && vistas.vencidos.length },
      ]} />
      <Alerta error={accion.error} />
      {error ? <ErrorBloque que="los préstamos" error={error} alReintentar={recargar} />
        : !datos ? <EsqueletoFilas filas={5} />
        : filas.length === 0 ? <Vacio>{vacios[vista]}</Vacio>
        : (
          <Tabla key={vista} etiqueta="Préstamos" columnas={columnas} filas={filas} clave={(p) => p.id}
                 movil={(p) => ({
                   titulo: p.nombreEquipo,
                   valor: <DevolverEl fecha={p.fechaDevolucionEsperada} hoy={hoy} />,
                   detalle: `${p.nombreUsuario} · #${p.id}`,
                   acciones: accionesPrestamo(p),
                 })} />
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

/** Pregunta: "¿Dónde está cada equipo?". Un equipo prestado dice quién lo tiene y cuándo vuelve. */
export function Equipos() {
  const { datos, error, recargar } = useLaboratorio();
  const accion = useAccion(recargar);
  const { avisar } = useAvisos();
  const edicion = useDialogo();
  const desactivacion = useDialogo();
  const [vista, setVista] = useState('TODOS');
  const hoy = hoyEnBogota();

  const equipos = datos?.equipos ?? [];
  const prestamoDe = Object.fromEntries((datos?.porDevolver ?? []).map((p) => [p.equipoId, p]));
  const cuenta = (estado) => equipos.filter((e) => e.estado === estado).length;
  const filas = vista === 'TODOS' ? equipos : equipos.filter((e) => e.estado === vista);

  const cambiarEstado = (eq, nuevo, texto) => accion.ejecutar(eq.id,
    () => api.cambiarEstadoEquipo(eq.id, nuevo), texto,
    () => api.cambiarEstadoEquipo(eq.id, eq.estado));

  function ubicacion(eq) {
    const p = prestamoDe[eq.id];
    if (p) {
      const cuando = cuandoDevolver(p.fechaDevolucionEsperada, hoy);
      return <>{p.nombreUsuario}<span className={cuando.dias < 0 ? 'sub atrasado' : 'sub'}>Vuelve: {cuando.texto.toLowerCase()}</span></>;
    }
    return <span className="tenue">{eq.estado === 'EN_MANTENIMIENTO' ? 'En mantenimiento' : 'En el laboratorio'}</span>;
  }

  function accionesEquipo(eq) {
    return (
      <>
        <button type="button" className="boton fantasma" onClick={() => edicion.abrir(eq)}>Editar</button>
        {eq.estado === 'DISPONIBLE' && (
          <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                  onClick={() => cambiarEstado(eq, 'EN_MANTENIMIENTO', `${eq.codigo} pasó a mantenimiento.`)}>
            A mantenimiento
          </button>
        )}
        {eq.estado === 'EN_MANTENIMIENTO' && (
          <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                  onClick={() => cambiarEstado(eq, 'DISPONIBLE', `${eq.codigo} volvió a estar disponible.`)}>
            Marcar disponible
          </button>
        )}
        {eq.estado !== 'PRESTADO' && (
          <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
                  onClick={() => desactivacion.abrir(eq)}>
            Desactivar
          </button>
        )}
      </>
    );
  }

  const columnas = [
    { titulo: 'Código', ancho: 112, celda: (eq) => <span className="mono">{eq.codigo}</span> },
    { titulo: 'Equipo', celda: (eq) => <>{eq.nombre}{eq.descripcion && <span className="sub recortar">{eq.descripcion}</span>}</> },
    { titulo: 'Categoría', celda: (eq) => CATEGORIAS[eq.categoria] },
    { titulo: 'Estado', celda: (eq) => <EstadoEquipo estado={eq.estado} /> },
    { titulo: 'Dónde está', celda: (eq) => ubicacion(eq) },
    { titulo: '', acciones: true, celda: (eq) => accionesEquipo(eq) },
  ];

  return (
    <>
      <Titulo titulo="Equipos" hallazgo={datos && `${equipos.length} equipos activos en el inventario.`}>
        <button type="button" className="boton primario" onClick={() => edicion.abrir({})}>Nuevo equipo</button>
      </Titulo>
      <Conteos etiqueta="Equipos por estado" valor={vista} alCambiar={setVista} opciones={[
        { valor: 'TODOS', texto: 'Todos', cuenta: datos && equipos.length },
        { valor: 'DISPONIBLE', texto: 'Disponibles', cuenta: datos && cuenta('DISPONIBLE') },
        { valor: 'PRESTADO', texto: 'Prestados', cuenta: datos && cuenta('PRESTADO') },
        { valor: 'EN_MANTENIMIENTO', texto: 'En mantenimiento', cuenta: datos && cuenta('EN_MANTENIMIENTO') },
      ]} />
      <Alerta error={accion.error} />
      {error ? <ErrorBloque que="los equipos" error={error} alReintentar={recargar} />
        : !datos ? <EsqueletoFilas filas={6} />
        : filas.length === 0 ? <Vacio>No hay equipos en este estado.</Vacio>
        : (
          <Tabla key={vista} etiqueta="Equipos" columnas={columnas} filas={filas} clave={(eq) => eq.id}
                 movil={(eq) => ({
                   titulo: eq.nombre,
                   valor: <EstadoEquipo estado={eq.estado} />,
                   detalle: <>{eq.codigo} · {CATEGORIAS[eq.categoria]} · {ubicacion(eq)}</>,
                   acciones: accionesEquipo(eq),
                 })} />
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
              <button type="button" className="boton" onClick={desactivacion.cerrar}>Cancelar</button>
              <button type="button" className="boton primario" onClick={async () => {
                const eq = desactivacion.dato;
                desactivacion.cerrar();
                await accion.ejecutar(eq.id, () => api.desactivarEquipo(eq.id), `${eq.codigo} quedó desactivado.`);
              }}>
                Desactivar {desactivacion.dato.codigo}
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
          <div className="campo">
            <label htmlFor="equipo-codigo">Código</label>
            {/* La API solo admite mayúsculas, números y guiones: se convierte mientras se escribe. */}
            <input id="equipo-codigo" className="entrada mono" value={datos.codigo} maxLength={30} pattern="[A-Z0-9\-]+"
                   required autoFocus aria-describedby="ayuda-codigo"
                   onChange={(e) => setDatos({ ...datos, codigo: e.target.value.toUpperCase().replace(/\s/g, '-') })} />
            <small id="ayuda-codigo" className="ayuda">Mayúsculas, números y guiones.</small>
          </div>
          <div className="campo">
            <label htmlFor="equipo-categoria">Categoría</label>
            <select id="equipo-categoria" className="entrada" value={datos.categoria} onChange={cambiar('categoria')}>
              {Object.entries(CATEGORIAS).map(([valor, texto]) => (
                <option key={valor} value={valor}>{texto}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="campo">
          <label htmlFor="equipo-nombre">Nombre</label>
          <input id="equipo-nombre" className="entrada" value={datos.nombre} onChange={cambiar('nombre')} maxLength={100}
                 required />
        </div>
        <div className="campo">
          <label htmlFor="equipo-descripcion">Descripción <span className="tenue">(opcional)</span></label>
          <textarea id="equipo-descripcion" className="entrada" value={datos.descripcion} onChange={cambiar('descripcion')}
                    maxLength={500} rows={3} />
        </div>
        <div className="botones">
          <button type="button" className="boton" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton primario" disabled={enviando}>
            {enviando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Dialogo>
  );
}

/** Pregunta: "¿Quién puede pedir equipos?". Desactivar es reversible, así que el aviso ofrece Deshacer. */
export function Estudiantes() {
  const consulta = useCallback(async () => {
    const [estudiantes, pendientes, porDevolver] = await Promise.all([
      api.listarEstudiantes(), api.prestamosPendientes(), api.prestamosPorDevolver(),
    ]);
    return { estudiantes, enCurso: [...pendientes, ...porDevolver] };
  }, []);
  const { datos, error, recargar } = useCarga(consulta);
  const accion = useAccion(recargar);
  const [vista, setVista] = useState('TODAS');

  const estudiantes = datos?.estudiantes ?? [];
  const enCurso = (id) => (datos?.enCurso ?? []).filter((p) => p.usuarioId === id).length;
  const activas = estudiantes.filter((u) => u.activo).length;
  const filas = vista === 'TODAS' ? estudiantes : estudiantes.filter((u) => u.activo === (vista === 'ACTIVAS'));

  function accionEstudiante(u) {
    return u.activo ? (
      <button type="button" className="boton fantasma" disabled={accion.ocupado !== null}
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
    );
  }

  const columnas = [
    {
      titulo: 'Estudiante',
      celda: (u) => (
        <span className="persona-celda">
          <Avatar nombre={u.nombre} id={u.id} />
          <span>{u.nombre}<span className="sub">{u.correo}</span></span>
        </span>
      ),
    },
    { titulo: 'Préstamos en curso', num: true, ancho: 160, celda: (u) => (enCurso(u.id) ? `${enCurso(u.id)} de 2` : <span className="tenue">—</span>) },
    { titulo: 'Cuenta', ancho: 140, celda: (u) => <span className={u.activo ? 'pildora pos' : 'pildora'}>{u.activo ? 'Activa' : 'Desactivada'}</span> },
    { titulo: '', acciones: true, celda: (u) => accionEstudiante(u) },
  ];

  return (
    <>
      <Titulo titulo="Estudiantes" hallazgo="Una cuenta desactivada no puede iniciar sesión ni pedir préstamos." />
      <Conteos etiqueta="Cuentas" valor={vista} alCambiar={setVista} opciones={[
        { valor: 'TODAS', texto: 'Todas', cuenta: datos && estudiantes.length },
        { valor: 'ACTIVAS', texto: 'Activas', cuenta: datos && activas },
        { valor: 'DESACTIVADAS', texto: 'Desactivadas', cuenta: datos && estudiantes.length - activas },
      ]} />
      <Alerta error={accion.error} />
      {error ? <ErrorBloque que="los estudiantes" error={error} alReintentar={recargar} />
        : !datos ? <EsqueletoFilas filas={5} />
        : filas.length === 0 ? <Vacio>No hay cuentas en esta vista.</Vacio>
        : (
          <Tabla etiqueta="Estudiantes" columnas={columnas} filas={filas} clave={(u) => u.id}
                 movil={(u) => ({
                   titulo: u.nombre,
                   valor: <span className={u.activo ? 'pildora pos' : 'pildora'}>{u.activo ? 'Activa' : 'Desactivada'}</span>,
                   detalle: `${u.correo}${enCurso(u.id) ? ` · ${enCurso(u.id)} de 2 en curso` : ''}`,
                   acciones: accionEstudiante(u),
                 })} />
        )}
    </>
  );
}
