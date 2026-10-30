import { NavLink, Navigate, Outlet } from 'react-router-dom';
import { useSesion } from '../auth/Sesion.jsx';

const MENU = {
  ESTUDIANTE: [
    { a: '/catalogo', texto: 'Catálogo' },
    { a: '/mis-prestamos', texto: 'Mis préstamos' },
  ],
  ADMIN: [
    { a: '/admin', texto: 'Panel', fin: true },
    { a: '/admin/prestamos', texto: 'Préstamos' },
    { a: '/admin/equipos', texto: 'Equipos' },
    { a: '/admin/estudiantes', texto: 'Estudiantes' },
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

export function Plantilla() {
  const { sesion, cerrarSesion } = useSesion();
  return (
    <div className="app">
      <header className="barra">
        <div className="barra-contenido">
          <NavLink to={inicioSegunRol(sesion.rol)} className="marca">
            <span className="logo" aria-hidden="true">PL</span>
            PrestaLab
          </NavLink>
          <nav aria-label="Principal">
            {MENU[sesion.rol].map((item) => (
              <NavLink key={item.a} to={item.a} end={item.fin}>
                {item.texto}
              </NavLink>
            ))}
          </nav>
          <div className="usuario">
            <span>
              {sesion.nombre}
              <small>{sesion.rol === 'ADMIN' ? 'Administrador' : 'Estudiante'}</small>
            </span>
            <button type="button" className="boton boton-secundario" onClick={() => cerrarSesion()}>
              Salir
            </button>
          </div>
        </div>
      </header>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  );
}
