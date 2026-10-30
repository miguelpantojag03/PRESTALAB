import { useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet } from 'react-router-dom';
import { motion } from 'motion/react';
import { useSesion } from '../auth/Sesion.jsx';
import { RESORTE_RAPIDO } from '../util/movimiento.js';

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

/** El borde de la barra solo aparece cuando hay contenido pasando por debajo de ella. */
function useDesplazado() {
  const [desplazado, setDesplazado] = useState(false);
  useEffect(() => {
    const revisar = () => setDesplazado(window.scrollY > 4);
    revisar();
    window.addEventListener('scroll', revisar, { passive: true });
    return () => window.removeEventListener('scroll', revisar);
  }, []);
  return desplazado;
}

export function Plantilla() {
  const { sesion, cerrarSesion } = useSesion();
  const desplazado = useDesplazado();
  return (
    <div className="app">
      <header className={desplazado ? 'barra con-contenido-debajo' : 'barra'}>
        <div className="barra-contenido">
          <NavLink to={inicioSegunRol(sesion.rol)} className="marca">
            <span className="logo" aria-hidden="true">PL</span>
            PrestaLab
          </NavLink>
          <nav aria-label="Principal">
            {MENU[sesion.rol].map((item) => (
              <NavLink key={item.a} to={item.a} end={item.fin}>
                {({ isActive }) => (
                  <>
                    {/* Una sola píldora que se desliza de una sección a otra: indica dónde estás y de dónde vienes. */}
                    {isActive && <motion.span layoutId="menu-activo" className="pildora" transition={RESORTE_RAPIDO} />}
                    <span className="menu-texto">{item.texto}</span>
                  </>
                )}
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
