import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { api } from '../api/cliente.js';
import { useSesion } from '../auth/Sesion.jsx';
import { RESORTE } from '../util/movimiento.js';
import {
  Avatar, IconoCatalogo, IconoEquipos, IconoEstudiantes, IconoHistorial, IconoMenu, IconoPanel, IconoPrestamos,
  IconoSalir, Marca,
} from './Iconos.jsx';

const MENU = {
  ESTUDIANTE: [
    { a: '/catalogo', texto: 'Catálogo', Icono: IconoCatalogo },
    { a: '/mis-prestamos', texto: 'Mis préstamos', Icono: IconoHistorial },
  ],
  ADMIN: [
    { a: '/admin', texto: 'Panel', Icono: IconoPanel, fin: true },
    { a: '/admin/prestamos', texto: 'Préstamos', Icono: IconoPrestamos, cola: true },
    { a: '/admin/equipos', texto: 'Equipos', Icono: IconoEquipos },
    { a: '/admin/estudiantes', texto: 'Estudiantes', Icono: IconoEstudiantes },
  ],
};

export function inicioSegunRol(rol) {
  return rol === 'ADMIN' ? '/admin' : '/catalogo';
}

/**
 * Deja pasar solo a quien tiene sesión y el rol pedido. Es una comodidad de navegación:
 * la seguridad real la aplica el backend con el JWT (403 ACCESO_DENEGADO).
 */
export function RutaProtegida({ rol }) {
  const { sesion } = useSesion();
  if (!sesion) {
    return <Navigate to="/login" replace />;
  }
  if (rol && sesion.rol !== rol) {
    return <Navigate to={inicioSegunRol(sesion.rol)} replace />;
  }
  return <Outlet />;
}

/** Solicitudes por aprobar: el único conteo del menú, porque es la única cola que pide una acción. */
const ContextoCola = createContext({ pendientes: null, recargarCola: () => {} });
export const useCola = () => useContext(ContextoCola);

function Menu({ items, pendientes, alElegir }) {
  return (
    <nav aria-label="Principal">
      {items.map(({ a, texto, Icono, fin, cola }) => (
        <NavLink key={a} to={a} end={fin} className="menu-item" onClick={alElegir}>
          <Icono />
          {texto}
          {cola && pendientes > 0 && (
            <span className="menu-cuenta" aria-label={`${pendientes} solicitudes por aprobar`}>{pendientes}</span>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

function Marcador({ sesion }) {
  return (
    <NavLink to={inicioSegunRol(sesion.rol)} className="marca">
      <Marca />
      <span>
        <strong>PrestaLab</strong>
        <small>{sesion.rol === 'ADMIN' ? 'Administración del laboratorio' : 'Préstamo de equipos'}</small>
      </span>
    </NavLink>
  );
}

function Persona({ sesion, alSalir }) {
  return (
    <div className="lateral-pie">
      <Avatar nombre={sesion.nombre} id={sesion.usuarioId} />
      <span className="persona">
        <strong>{sesion.nombre}</strong>
        <small>{sesion.rol === 'ADMIN' ? 'Administrador' : 'Estudiante'}</small>
      </span>
      <button type="button" className="boton fantasma" onClick={alSalir} aria-label="Cerrar sesión" title="Cerrar sesión">
        <IconoSalir />
      </button>
    </div>
  );
}

export function Plantilla() {
  const { sesion, cerrarSesion } = useSesion();
  const ubicacion = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [pendientes, setPendientes] = useState(null);
  const esAdmin = sesion.rol === 'ADMIN';
  const items = MENU[sesion.rol];
  const actual = items.find((i) => (i.fin ? ubicacion.pathname === i.a : ubicacion.pathname.startsWith(i.a)));

  const recargarCola = useCallback(async () => {
    if (!esAdmin) {
      return;
    }
    try {
      setPendientes((await api.prestamosPendientes()).length);
    } catch {
      // El conteo es una ayuda; si falla, la pantalla de préstamos muestra su propio error.
    }
  }, [esAdmin]);

  useEffect(() => {
    recargarCola();
  }, [recargarCola, ubicacion.pathname]);

  useEffect(() => {
    if (!menuAbierto) {
      return undefined;
    }
    const alTeclear = (e) => e.key === 'Escape' && setMenuAbierto(false);
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [menuAbierto]);

  return (
    <ContextoCola.Provider value={{ pendientes, recargarCola }}>
      <div className="app">
        <aside className="lateral">
          <Marcador sesion={sesion} />
          <Menu items={items} pendientes={pendientes} />
          <Persona sesion={sesion} alSalir={() => cerrarSesion()} />
        </aside>

        {/* En el celular el marco se pliega: barra de 48px con el menú y el nombre de la pantalla. */}
        <header className="barra-movil">
          <button type="button" className="boton-menu" aria-label="Abrir el menú" aria-expanded={menuAbierto}
                  onClick={() => setMenuAbierto(true)}>
            <IconoMenu />
          </button>
          <strong>{actual?.texto ?? 'PrestaLab'}</strong>
        </header>
        <AnimatePresence>
          {menuAbierto && (
            <>
              <motion.div key="velo" className="velo" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }} transition={RESORTE} onPointerDown={() => setMenuAbierto(false)} />
              {/* Entra desde la izquierda, donde está el botón, y sale por el mismo lado. */}
              <motion.div key="menu" className="menu-movil" role="dialog" aria-modal="true" aria-label="Menú"
                          initial={{ x: '-100%' }} animate={{ x: 0 }} exit={{ x: '-100%' }} transition={RESORTE}>
                <Marcador sesion={sesion} />
                <Menu items={items} pendientes={pendientes} alElegir={() => setMenuAbierto(false)} />
                <Persona sesion={sesion} alSalir={() => cerrarSesion()} />
              </motion.div>
            </>
          )}
        </AnimatePresence>

        <main className="hoja-trabajo">
          <Outlet />
        </main>
      </div>
    </ContextoCola.Provider>
  );
}
