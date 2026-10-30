import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, api, configurarCliente } from '../api/cliente.js';
import { leerPayload, sesionDesdeToken } from '../auth/token.js';
import { formatearFecha, formatearFechaHora, hoyEnBogota, sumarDias } from '../util/formato.js';
import { tokenDePrueba } from './ayudas.js';

describe('token', () => {
  it('lee el payload aunque tenga tildes (base64url y UTF-8)', () => {
    const token = tokenDePrueba({ sub: '7', nombre: 'Laura Gómez Núñez', rol: 'ESTUDIANTE', exp: 2_000_000_000 });
    expect(leerPayload(token).nombre).toBe('Laura Gómez Núñez');
  });

  it('arma la sesión con el id, el nombre y el rol', () => {
    const token = tokenDePrueba({ sub: '1', nombre: 'Admin', rol: 'ADMIN', exp: 2_000 });
    expect(sesionDesdeToken(token, 1_000)).toEqual({ token, usuarioId: 1, nombre: 'Admin', rol: 'ADMIN', expira: 2_000 });
  });

  it('descarta un token vencido o ilegible', () => {
    expect(sesionDesdeToken(tokenDePrueba({ sub: '1', rol: 'ADMIN', exp: 1_000 }), 1_000)).toBeNull();
    expect(sesionDesdeToken('esto-no-es-un-jwt')).toBeNull();
    expect(sesionDesdeToken(null)).toBeNull();
  });
});

describe('formato', () => {
  it('calcula hoy en Bogotá aunque en UTC ya sea el día siguiente', () => {
    // 2026-03-10 02:00 UTC son las 21:00 del 9 de marzo en Bogotá (UTC-5).
    expect(hoyEnBogota(new Date('2026-03-10T02:00:00Z'))).toBe('2026-03-09');
  });

  it('suma días cruzando meses y años', () => {
    expect(sumarDias('2026-12-28', 7)).toBe('2027-01-04');
    expect(sumarDias('2028-02-27', 2)).toBe('2028-02-29');
  });

  it('muestra fechas en formato dd/mm/aaaa', () => {
    expect(formatearFecha('2026-11-05')).toBe('05/11/2026');
    expect(formatearFechaHora('2026-11-05T14:30:12.5')).toBe('05/11/2026 14:30');
    expect(formatearFecha(null)).toBe('—');
  });
});

describe('cliente de la API', () => {
  afterEach(() => vi.unstubAllGlobals());

  const responder = (estado, cuerpo) =>
    vi.fn().mockResolvedValue(new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado }));

  it('envía el token en Authorization y el cuerpo como JSON', async () => {
    const fetchFalso = responder(201, { id: 9 });
    vi.stubGlobal('fetch', fetchFalso);
    configurarCliente({ token: () => 'abc', noAutenticado: () => {} });

    await api.solicitarPrestamo({ equipoId: 5, fechaDevolucionEsperada: '2026-11-05' });

    const [url, opciones] = fetchFalso.mock.calls[0];
    expect(url).toBe('/api/v1/prestamos');
    expect(opciones.method).toBe('POST');
    expect(opciones.headers.Authorization).toBe('Bearer abc');
    expect(JSON.parse(opciones.body)).toEqual({ equipoId: 5, fechaDevolucionEsperada: '2026-11-05' });
  });

  it('convierte un ProblemDetail en ErrorApi con el código y los errores por campo', async () => {
    vi.stubGlobal('fetch', responder(400, {
      status: 400, codigo: 'VALIDACION', detail: 'La petición tiene campos inválidos',
      errores: { nombre: 'El nombre es obligatorio' },
    }));
    configurarCliente({ token: () => null, noAutenticado: () => {} });

    const error = await api.crearEquipo({}).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    expect(error.estado).toBe(400);
    expect(error.codigo).toBe('VALIDACION');
    expect(error.errores).toEqual({ nombre: 'El nombre es obligatorio' });
  });

  it('cierra la sesión ante un 401 NO_AUTENTICADO, pero no por credenciales incorrectas', async () => {
    const noAutenticado = vi.fn();
    configurarCliente({ token: () => 'vencido', noAutenticado });

    vi.stubGlobal('fetch', responder(401, { codigo: 'NO_AUTENTICADO', detail: 'Se requiere un token' }));
    await api.misPrestamos().catch(() => {});
    expect(noAutenticado).toHaveBeenCalledTimes(1);

    vi.stubGlobal('fetch', responder(401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'Correo o contraseña incorrectos' }));
    await api.iniciarSesion({ correo: 'a@b.co', password: 'x' }).catch(() => {});
    expect(noAutenticado).toHaveBeenCalledTimes(1);
  });

  it('devuelve null en un 204 y arma los filtros del catálogo', async () => {
    const fetchFalso = responder(204);
    vi.stubGlobal('fetch', fetchFalso);
    configurarCliente({ token: () => 't', noAutenticado: () => {} });

    expect(await api.cambiarEstadoEquipo(3, 'EN_MANTENIMIENTO')).toBeNull();
    await api.listarEquipos({ categoria: 'CAMARA', soloDisponibles: true }).catch(() => {});
    await api.listarEquipos({ categoria: '', soloDisponibles: false }).catch(() => {});

    expect(fetchFalso.mock.calls[1][0]).toBe('/api/v1/equipos?categoria=CAMARA&soloDisponibles=true');
    expect(fetchFalso.mock.calls[2][0]).toBe('/api/v1/equipos?soloDisponibles=false');
  });

  it('informa la falta de conexión con un mensaje claro', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const error = await api.resumen().catch((e) => e);
    expect(error.codigo).toBe('SIN_CONEXION');
    expect(error.message).toBe('No hay conexión con el servidor');
  });
});
