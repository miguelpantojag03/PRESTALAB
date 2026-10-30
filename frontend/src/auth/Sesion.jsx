import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, configurarCliente } from '../api/cliente.js';
import { sesionDesdeToken } from './token.js';

const CLAVE = 'prestalab.token';
const ContextoSesion = createContext(null);

function tokenGuardado() {
  try {
    return sessionStorage.getItem(CLAVE);
  } catch {
    return null;
  }
}

function guardarToken(token) {
  try {
    if (token) {
      sessionStorage.setItem(CLAVE, token);
    } else {
      sessionStorage.removeItem(CLAVE);
    }
  } catch {
    // Sin almacenamiento (modo privado estricto) la sesión dura lo que dure la pestaña.
  }
}

/**
 * Guarda el JWT en sessionStorage (se borra al cerrar la pestaña) y cierra la sesión sola cuando el token vence
 * o la API responde 401.
 */
export function ProveedorSesion({ children }) {
  const [sesion, setSesion] = useState(() => sesionDesdeToken(tokenGuardado()));
  const [motivoCierre, setMotivoCierre] = useState(null);

  const cerrarSesion = useCallback((motivo = null) => {
    guardarToken(null);
    setSesion(null);
    setMotivoCierre(motivo);
  }, []);

  const iniciarSesion = useCallback(async (correo, password) => {
    const respuesta = await api.iniciarSesion({ correo, password });
    const nueva = sesionDesdeToken(respuesta.token);
    guardarToken(respuesta.token);
    setMotivoCierre(null);
    setSesion(nueva);
    return nueva;
  }, []);

  // El cliente se configura en el primer render y lee la sesión por referencia: los useEffect de las páginas
  // corren antes que los de este proveedor, así que configurarlo en un efecto haría salir la primera petición
  // sin token.
  const sesionActual = useRef(sesion);
  sesionActual.current = sesion;
  useState(() =>
    configurarCliente({
      token: () => sesionActual.current?.token ?? null,
      noAutenticado: () => cerrarSesion('Tu sesión venció. Inicia sesión de nuevo.'),
    }),
  );

  useEffect(() => {
    if (!sesion) {
      return undefined;
    }
    const restante = sesion.expira * 1000 - Date.now();
    const temporizador = setTimeout(() => cerrarSesion('Tu sesión venció. Inicia sesión de nuevo.'), restante);
    return () => clearTimeout(temporizador);
  }, [sesion, cerrarSesion]);

  const valor = useMemo(
    () => ({ sesion, iniciarSesion, cerrarSesion, motivoCierre }),
    [sesion, iniciarSesion, cerrarSesion, motivoCierre],
  );
  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>;
}

export function useSesion() {
  return useContext(ContextoSesion);
}
