import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { ProveedorSesion } from '../auth/Sesion.jsx';
import { FUTURO_ROUTER, tokenDePrueba } from './ayudas.js';

const DENTRO_DE_UNA_HORA = Math.floor(Date.now() / 1000) + 3600;
const TOKEN_ADMIN = tokenDePrueba({ sub: '1', nombre: 'Administrador', rol: 'ADMIN', exp: DENTRO_DE_UNA_HORA });
const TOKEN_ESTUDIANTE = tokenDePrueba({ sub: '7', nombre: 'Laura Gómez', rol: 'ESTUDIANTE', exp: DENTRO_DE_UNA_HORA });

/** Simula la API: cada ruta "MÉTODO /api/v1/..." responde con [estado, cuerpo]. */
function simularApi(rutas) {
  const fetchFalso = vi.fn(async (url, opciones = {}) => {
    const clave = `${opciones.method || 'GET'} ${url}`;
    const respuesta = rutas[clave];
    if (!respuesta) {
      return new Response(JSON.stringify({ codigo: 'RECURSO_NO_ENCONTRADO', detail: `Sin simular: ${clave}` }),
        { status: 404 });
    }
    const [estado, cuerpo] = typeof respuesta === 'function' ? respuesta(opciones) : respuesta;
    return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado });
  });
  vi.stubGlobal('fetch', fetchFalso);
  return fetchFalso;
}

function montar(ruta) {
  return render(
    <MemoryRouter initialEntries={[ruta]} future={FUTURO_ROUTER}>
      <ProveedorSesion>
        <App />
      </ProveedorSesion>
    </MemoryRouter>,
  );
}

const EQUIPO = {
  id: 5, codigo: 'PORT-001', nombre: 'Portátil Lenovo', descripcion: null,
  categoria: 'PORTATIL', estado: 'DISPONIBLE', activo: true,
};

describe('flujos de la interfaz', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('sin sesión cualquier ruta lleva al inicio de sesión', async () => {
    simularApi({});
    montar('/admin/equipos');
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
  });

  it('el administrador inicia sesión, ve el panel y la primera petición ya lleva el token', async () => {
    const fetchFalso = simularApi({
      'POST /api/v1/auth/login': [200, { token: TOKEN_ADMIN, tipo: 'Bearer', expiraEnSegundos: 3600, rol: 'ADMIN' }],
      'GET /api/v1/dashboard/resumen': [200, { disponibles: 6, prestados: 2, enMantenimiento: 1, vencidos: 0 }],
      'GET /api/v1/prestamos/pendientes': [200, []],
    });
    montar('/login');

    await userEvent.type(screen.getByLabelText('Correo'), 'admin@prestalab.local');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'Admin123*');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Panel del laboratorio' })).toBeTruthy();
    expect(await screen.findByText('6')).toBeTruthy();
    const resumen = fetchFalso.mock.calls.find(([url]) => url === '/api/v1/dashboard/resumen');
    expect(resumen[1].headers.Authorization).toBe(`Bearer ${TOKEN_ADMIN}`);
  });

  it('muestra el mensaje uniforme del backend cuando las credenciales fallan', async () => {
    simularApi({
      'POST /api/v1/auth/login': [401, { codigo: 'CREDENCIALES_INVALIDAS', detail: 'Correo o contraseña incorrectos' }],
    });
    montar('/login');

    await userEvent.type(screen.getByLabelText('Correo'), 'nadie@uni.edu.co');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'incorrecta');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect((await screen.findByRole('alert')).textContent).toBe('Correo o contraseña incorrectos');
  });

  it('un estudiante no entra a las pantallas del administrador', async () => {
    sessionStorage.setItem('prestalab.token', TOKEN_ESTUDIANTE);
    simularApi({ 'GET /api/v1/equipos?soloDisponibles=true': [200, [EQUIPO]] });
    montar('/admin');
    expect(await screen.findByRole('heading', { name: 'Catálogo de equipos' })).toBeTruthy();
  });

  it('el estudiante solicita un préstamo desde el catálogo (RF04)', async () => {
    sessionStorage.setItem('prestalab.token', TOKEN_ESTUDIANTE);
    let enviado = null;
    simularApi({
      'GET /api/v1/equipos?soloDisponibles=true': [200, [EQUIPO]],
      'POST /api/v1/prestamos': (opciones) => {
        enviado = JSON.parse(opciones.body);
        return [201, { id: 30, nombreEquipo: 'Portátil Lenovo', estado: 'PENDIENTE' }];
      },
    });
    montar('/catalogo');

    await userEvent.click(await screen.findByRole('button', { name: 'Solicitar préstamo' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud' }));

    expect(await screen.findByText(/Quedó PENDIENTE de aprobación/)).toBeTruthy();
    expect(enviado.equipoId).toBe(5);
    expect(enviado.fechaDevolucionEsperada).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('muestra la regla de negocio que devuelve la API (422)', async () => {
    sessionStorage.setItem('prestalab.token', TOKEN_ESTUDIANTE);
    simularApi({
      'GET /api/v1/equipos?soloDisponibles=true': [200, [EQUIPO]],
      'POST /api/v1/prestamos': [422, { codigo: 'REGLA_NEGOCIO', detail: 'Ya tienes 2 préstamos activos' }],
    });
    montar('/catalogo');

    await userEvent.click(await screen.findByRole('button', { name: 'Solicitar préstamo' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud' }));

    expect((await screen.findByRole('alert')).textContent).toContain('Ya tienes 2 préstamos activos');
  });

  it('si el token vence en el servidor, vuelve al inicio de sesión con un aviso', async () => {
    sessionStorage.setItem('prestalab.token', TOKEN_ESTUDIANTE);
    simularApi({
      'GET /api/v1/prestamos/mios': [401, { codigo: 'NO_AUTENTICADO', detail: 'Se requiere un token JWT válido' }],
    });
    montar('/mis-prestamos');

    expect(await screen.findByText('Tu sesión venció. Inicia sesión de nuevo.')).toBeTruthy();
    await waitFor(() => expect(sessionStorage.getItem('prestalab.token')).toBeNull());
  });
});
