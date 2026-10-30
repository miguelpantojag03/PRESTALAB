// Cliente HTTP de la API /api/v1. Todos los errores de la API llegan como ProblemDetail (RFC 9457,
// ADR 0003): se convierten en ErrorApi con el código estable, el mensaje y los errores por campo.

const BASE = '/api/v1';

export class ErrorApi extends Error {
  constructor(estado, problema) {
    super(problema?.detail || 'No se pudo completar la operación');
    this.name = 'ErrorApi';
    this.estado = estado;
    this.codigo = problema?.codigo || 'ERROR_INTERNO';
    this.errores = problema?.errores || {};
  }
}

let obtenerToken = () => null;
let alNoAutenticado = () => {};

/** La sesión registra cómo leer el token y qué hacer cuando la API responde 401. */
export function configurarCliente({ token, noAutenticado }) {
  obtenerToken = token;
  alNoAutenticado = noAutenticado;
}

export async function solicitar(metodo, ruta, cuerpo) {
  const cabeceras = { Accept: 'application/json' };
  const token = obtenerToken();
  if (token) {
    cabeceras.Authorization = `Bearer ${token}`;
  }
  if (cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json';
  }

  let respuesta;
  try {
    respuesta = await fetch(BASE + ruta, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
  } catch {
    throw new ErrorApi(0, { detail: 'No hay conexión con el servidor', codigo: 'SIN_CONEXION' });
  }

  if (respuesta.status === 204) {
    return null;
  }
  const texto = await respuesta.text();
  const datos = texto ? JSON.parse(texto) : null;
  if (!respuesta.ok) {
    const error = new ErrorApi(respuesta.status, datos);
    // Un 401 fuera del login significa que el token venció o es inválido: se cierra la sesión.
    if (respuesta.status === 401 && error.codigo === 'NO_AUTENTICADO') {
      alNoAutenticado();
    }
    throw error;
  }
  return datos;
}

const consulta = (parametros) => {
  const texto = new URLSearchParams(
    Object.entries(parametros).filter(([, valor]) => valor !== undefined && valor !== ''),
  ).toString();
  return texto ? `?${texto}` : '';
};

/** Los 19 endpoints de la API, agrupados como en Swagger. */
export const api = {
  registrar: (datos) => solicitar('POST', '/auth/registro', datos),
  iniciarSesion: (datos) => solicitar('POST', '/auth/login', datos),

  listarEquipos: (filtros = {}) => solicitar('GET', `/equipos${consulta(filtros)}`),
  obtenerEquipo: (id) => solicitar('GET', `/equipos/${id}`),
  crearEquipo: (datos) => solicitar('POST', '/equipos', datos),
  actualizarEquipo: (id, datos) => solicitar('PUT', `/equipos/${id}`, datos),
  cambiarEstadoEquipo: (id, estado) => solicitar('PATCH', `/equipos/${id}/estado`, { estado }),
  desactivarEquipo: (id) => solicitar('POST', `/equipos/${id}/desactivar`),

  solicitarPrestamo: (datos) => solicitar('POST', '/prestamos', datos),
  misPrestamos: () => solicitar('GET', '/prestamos/mios'),
  prestamosPendientes: () => solicitar('GET', '/prestamos/pendientes'),
  prestamosPorDevolver: () => solicitar('GET', '/prestamos/por-devolver'),
  aprobarPrestamo: (id) => solicitar('POST', `/prestamos/${id}/aprobar`),
  rechazarPrestamo: (id, motivo) => solicitar('POST', `/prestamos/${id}/rechazar`, { motivo }),
  devolverPrestamo: (id) => solicitar('POST', `/prestamos/${id}/devolver`),

  resumen: () => solicitar('GET', '/dashboard/resumen'),

  listarEstudiantes: () => solicitar('GET', '/usuarios/estudiantes'),
  activarUsuario: (id) => solicitar('POST', `/usuarios/${id}/activar`),
  desactivarUsuario: (id) => solicitar('POST', `/usuarios/${id}/desactivar`),
};
