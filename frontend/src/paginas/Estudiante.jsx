import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { api } from '../api/cliente.js';
import {
  Alerta, Conteos, Dialogo, ErrorBloque, EsqueletoFilas, EstadoEquipo, EstadoPrestamo, Tabla, Titulo, Vacio,
  useAvisos, useDialogo,
} from '../componentes/Comunes.jsx';
import {
  CATEGORIAS, CATEGORIAS_PLURAL, cuandoDevolver, fechaCorta, formatearFecha, formatearFechaHora, hoyEnBogota, sumarDias,
} from '../util/formato.js';
import { IconoCatalogo, IconoHistorial } from '../componentes/Iconos.jsx';
import { RESORTE } from '../util/movimiento.js';
import { useCarga } from '../util/useCarga.js';

const DIAS_MAXIMOS = 7;
const MAXIMO_ACTIVOS = 2;
const EN_CURSO = ['PENDIENTE', 'APROBADO', 'VENCIDO'];

/** Pregunta: "¿Qué me puedo llevar hoy?". Arriba, su cupo de préstamos (RN01), antes de intentar. */
export function Catalogo() {
  const consulta = useCallback(async () => {
    const [equipos, mios] = await Promise.all([api.listarEquipos(), api.misPrestamos()]);
    return { equipos, mios };
  }, []);
  const { datos, error, recargar } = useCarga(consulta);
  const [categoria, setCategoria] = useState('TODAS');
  const [soloDisponibles, setSoloDisponibles] = useState(true);
  const solicitud = useDialogo();
  const { avisar } = useAvisos();
  const navegar = useNavigate();

  const visibles = (datos?.equipos ?? []).filter((e) => !soloDisponibles || e.estado === 'DISPONIBLE');
  const filas = categoria === 'TODAS' ? visibles : visibles.filter((e) => e.categoria === categoria);
  const activos = (datos?.mios ?? []).filter((p) => p.estado === 'PENDIENTE' || p.estado === 'APROBADO').length;
  const vencido = (datos?.mios ?? []).some((p) => p.estado === 'VENCIDO');
  const sinCupo = activos >= MAXIMO_ACTIVOS || vencido;
  const opciones = [
    { valor: 'TODAS', texto: 'Todos', cuenta: datos && visibles.length },
    ...Object.keys(CATEGORIAS).map((c) => ({
      valor: c, texto: CATEGORIAS_PLURAL[c], cuenta: datos && visibles.filter((e) => e.categoria === c).length,
    })),
  ];

  const hallazgo = !datos ? null
    : vencido ? <span className="atrasado">Tienes un préstamo vencido: devuélvelo para poder pedir otro.</span>
    : activos >= MAXIMO_ACTIVOS ? 'Ya tienes 2 préstamos activos: devuelve uno para pedir otro.'
    : `Puedes pedir ${MAXIMO_ACTIVOS - activos} ${MAXIMO_ACTIVOS - activos === 1 ? 'equipo más' : 'equipos'}, cada uno hasta por 7 días.`;

  return (
    <>
      {/* El cupo (RN01) va en un callout: es lo primero que el estudiante necesita saber antes de pedir. */}
      <Titulo titulo="Catálogo" icono={<IconoCatalogo />}
              hallazgo={datos && `${activos} de ${MAXIMO_ACTIVOS} préstamos activos`}
              callout={datos && {
                tono: sinCupo ? 'durazno' : 'menta',
                contenido: (
                  <>
                    <span className="cupo-pista" aria-hidden="true">
                      {Array.from({ length: MAXIMO_ACTIVOS }, (_, i) => <span key={i} className={i < activos ? 'lleno' : undefined} />)}
                    </span>
                    {hallazgo}
                    {activos > 0 && <> <Link to="/mis-prestamos" className="enlace">Ver los míos</Link></>}
                  </>
                ),
              }}>
        <label className="interruptor">
          <input type="checkbox" role="switch" checked={soloDisponibles} onChange={(e) => setSoloDisponibles(e.target.checked)} />
          Solo disponibles
        </label>
      </Titulo>
      <Conteos etiqueta="Categorías" valor={categoria} alCambiar={setCategoria} opciones={opciones} />
      {error ? <ErrorBloque que="el catálogo" error={error} alReintentar={recargar} />
        : !datos ? <EsqueletoFilas filas={3} />
        : filas.length === 0 ? <Vacio>{soloDisponibles ? 'No hay equipos disponibles en esta categoría ahora mismo.' : 'No hay equipos en esta categoría.'}</Vacio>
        : (
          // Al filtrar, las tarjetas que quedan se deslizan a su lugar: es la respuesta al filtro, no una entrada.
          <motion.div layout className="rejilla">
            <AnimatePresence initial={false} mode="popLayout">
              {filas.map((equipo) => (
                <motion.article key={equipo.id} layout className="equipo" transition={RESORTE}
                                initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                  <div className="equipo-cabeza">
                    <span className="mono tenue">{equipo.codigo}</span>
                    <EstadoEquipo estado={equipo.estado} />
                  </div>
                  <div className="equipo-cuerpo">
                    <h2>{equipo.nombre}</h2>
                    <p className="tenue">{CATEGORIAS[equipo.categoria]}</p>
                    {equipo.descripcion && <p>{equipo.descripcion}</p>}
                  </div>
                  <button type="button" className="boton" disabled={equipo.estado !== 'DISPONIBLE' || sinCupo}
                          title={sinCupo ? 'Ya usaste tu cupo de préstamos' : undefined}
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
          <input id="fecha-devolucion" className="entrada" type="date" value={fecha} min={hoy} max={limite}
                 onChange={(e) => setFecha(e.target.value)} required aria-invalid={fueraDeRango}
                 aria-describedby="ayuda-fecha" />
          <small id="ayuda-fecha" className={fueraDeRango ? 'ayuda ayuda-invalido' : 'ayuda'}>
            Entre hoy y el {fechaCorta(limite)} (máximo 7 días).
          </small>
        </div>
        <div className="botones">
          <button type="button" className="boton" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton primario" disabled={enviando || fueraDeRango}>
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

/** Pregunta: "¿Qué tengo y cuándo lo devuelvo?". Primero lo que está en curso, con la fecha en palabras. */
export function MisPrestamos() {
  const { datos: prestamos, error, recargar } = useCarga(historialOrdenado);
  const hoy = hoyEnBogota();
  const enCurso = (prestamos ?? []).filter((p) => EN_CURSO.includes(p.estado));
  const historial = (prestamos ?? []).filter((p) => !EN_CURSO.includes(p.estado));
  const proximo = enCurso.filter((p) => p.estado !== 'PENDIENTE')
    .sort((a, b) => a.fechaDevolucionEsperada.localeCompare(b.fechaDevolucionEsperada))[0];

  const hallazgo = !prestamos ? null
    : proximo ? <>Devuelve <strong>{proximo.nombreEquipo}</strong>: {cuandoDevolver(proximo.fechaDevolucionEsperada, hoy).texto.toLowerCase()} ({fechaCorta(proximo.fechaDevolucionEsperada)}).</>
    : enCurso.length ? 'Tu solicitud espera la aprobación del laboratorio.'
    : 'No tienes préstamos en curso.';

  const detalle = (p) => {
    switch (p.estado) {
      case 'APROBADO': return `Aprobado el ${formatearFechaHora(p.fechaAprobacion)}`;
      case 'RECHAZADO': return `Motivo: ${p.motivoRechazo}`;
      case 'DEVUELTO': return `Devuelto el ${formatearFecha(p.fechaDevolucionReal)}`;
      case 'VENCIDO': return 'Pasó la fecha de devolución: devuélvelo cuanto antes';
      default: return 'Esperando aprobación';
    }
  };

  const columnas = [
    { titulo: 'Préstamo', ancho: 96, celda: (p) => <span className="mono">#{p.id}</span> },
    { titulo: 'Equipo', celda: (p) => <>{p.nombreEquipo}<span className="sub mono">{p.codigoEquipo}</span></> },
    { titulo: 'Solicitado', num: true, celda: (p) => formatearFecha(p.fechaSolicitud) },
    {
      titulo: 'Devolver',
      num: true,
      celda: (p) => {
        if (!EN_CURSO.includes(p.estado)) return formatearFecha(p.fechaDevolucionEsperada);
        const cuando = cuandoDevolver(p.fechaDevolucionEsperada, hoy);
        return <><span className={cuando.dias < 0 ? 'atrasado' : undefined}>{cuando.texto}</span><span className="sub">{fechaCorta(p.fechaDevolucionEsperada)}</span></>;
      },
    },
    { titulo: 'Estado', celda: (p) => <EstadoPrestamo estado={p.estado} /> },
    { titulo: 'Detalle', celda: (p) => <span className="tenue">{detalle(p)}</span> },
  ];

  const movil = (p) => ({
    titulo: p.nombreEquipo,
    valor: <EstadoPrestamo estado={p.estado} />,
    detalle: `#${p.id} · devolver el ${fechaCorta(p.fechaDevolucionEsperada)} · ${detalle(p)}`,
  });

  return (
    <>
      <Titulo titulo="Mis préstamos" icono={<IconoHistorial />}
              callout={prestamos && { tono: proximo ? 'amarillo' : '', contenido: hallazgo }}>
        <Link to="/catalogo" className="boton primario">Pedir un equipo</Link>
      </Titulo>
      {error ? <ErrorBloque que="tus préstamos" error={error} alReintentar={recargar} />
        : !prestamos ? <EsqueletoFilas filas={4} />
        : (
          <div className="reticula">
            <section className="ocupa-12" aria-labelledby="t-curso">
              <h2 id="t-curso" className="seccion-titulo">En curso</h2>
              {enCurso.length === 0 ? <Vacio>No tienes préstamos en curso.</Vacio>
                : <Tabla etiqueta="En curso" columnas={columnas} filas={enCurso} clave={(p) => p.id} movil={movil} />}
            </section>
            <section className="ocupa-12" aria-labelledby="t-historial">
              <h2 id="t-historial" className="seccion-titulo">Historial</h2>
              {historial.length === 0 ? <Vacio>Aquí aparecerán tus préstamos devueltos y rechazados.</Vacio>
                : <Tabla etiqueta="Historial" columnas={columnas} filas={historial} clave={(p) => p.id} movil={movil} />}
            </section>
          </div>
        )}
    </>
  );
}
