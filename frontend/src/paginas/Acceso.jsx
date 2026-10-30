import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAnimate } from 'motion/react';
import { api } from '../api/cliente.js';
import { useSesion } from '../auth/Sesion.jsx';
import { Alerta } from '../componentes/Comunes.jsx';
import { Marca } from '../componentes/Iconos.jsx';
import { inicioSegunRol } from '../componentes/Plantilla.jsx';

const MAXIMO_BYTES_PASSWORD = 72;

/**
 * Sacudida horizontal, como el inicio de sesión de macOS: la causa (credenciales rechazadas) es evidente y el
 * movimiento dice "no" sin palabras. No se hace con "reducir movimiento" activado; el mensaje basta.
 */
function useSacudida() {
  const [alcance, animar] = useAnimate();
  const sacudir = () => {
    const reducir = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!reducir && alcance.current) {
      animar(alcance.current, { x: [0, -10, 9, -6, 4, -2, 0] }, { duration: 0.45, ease: 'easeOut' });
    }
  };
  return [alcance, sacudir];
}

/**
 * La banda azul marino de Notion con sus puntos de colores, junto al formulario. El titular de la banda es un
 * párrafo: el único h1 de la página es el del formulario.
 */
function MarcoAcceso({ titulo, alcance, children }) {
  return (
    <div className="acceso">
      <aside className="acceso-banda">
        <div className="acceso-marca">
          <Marca tamano={32} />
          <div>
            <strong>PrestaLab</strong>
            <small>Laboratorio universitario</small>
          </div>
        </div>
        <div>
          <p className="banda-titulo">Pide el equipo. Devuélvelo a tiempo.</p>
          <p className="banda-sub">Portátiles, cámaras, proyectores y kits del laboratorio, por hasta 7 días.</p>
        </div>
        {['p1', 'p2', 'p3', 'p4', 'p5', 'p6'].map((p) => <span key={p} className={`punto ${p}`} aria-hidden="true" />)}
      </aside>
      <main className="acceso-panel">
        <div className="acceso-tarjeta" ref={alcance}>
          <h1>{titulo}</h1>
          {children}
        </div>
      </main>
    </div>
  );
}

/** RF01: inicio de sesión. Cualquier fallo de credenciales muestra el mismo mensaje del backend (ADR 0005). */
export function IniciarSesion() {
  const { sesion, iniciarSesion, motivoCierre } = useSesion();
  const navegar = useNavigate();
  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [alcance, sacudir] = useSacudida();

  if (sesion) {
    return <Navigate to={inicioSegunRol(sesion.rol)} replace />;
  }

  async function enviar(evento) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const nueva = await iniciarSesion(correo, password);
      navegar(inicioSegunRol(nueva.rol), { replace: true });
    } catch (e) {
      setError(e);
      setEnviando(false);
      if (e.codigo === 'CREDENCIALES_INVALIDAS') {
        sacudir();
      }
    }
  }

  return (
    <MarcoAcceso titulo="Iniciar sesión" alcance={alcance}>
      {motivoCierre && !error && <Alerta tipo="info">{motivoCierre}</Alerta>}
      <Alerta error={error} />
      <form onSubmit={enviar} className="formulario">
        <div className="campo">
          <label htmlFor="login-correo">Correo</label>
          <input id="login-correo" className="entrada" type="email" value={correo}
                 onChange={(e) => setCorreo(e.target.value)} autoComplete="username" required autoFocus />
        </div>
        <div className="campo">
          <label htmlFor="login-password">Contraseña</label>
          <input id="login-password" className="entrada" type="password" value={password}
                 onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </div>
        <button type="submit" className="boton primario" disabled={enviando}>
          {enviando ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
      <p className="pie-acceso">
        ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
      </p>
    </MarcoAcceso>
  );
}

/** RF01: registro. La API siempre crea un ESTUDIANTE; el formulario no envía el rol. */
export function Registro() {
  const { sesion, iniciarSesion } = useSesion();
  const navegar = useNavigate();
  const [datos, setDatos] = useState({ nombre: '', correo: '', password: '' });
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  if (sesion) {
    return <Navigate to={inicioSegunRol(sesion.rol)} replace />;
  }

  const cambiar = (campo) => (e) => setDatos({ ...datos, [campo]: e.target.value });

  // Validación en línea mientras se escribe, no al enviar. Los 72 bytes son el límite real de BCrypt en el backend.
  const largo = datos.password.length;
  const bytes = new TextEncoder().encode(datos.password).length;
  const avisoPassword = largo === 0
    ? { tipo: 'tenue', texto: 'Mínimo 8 caracteres.' }
    : largo < 8
      ? { tipo: 'tenue', texto: `Faltan ${8 - largo} caracter${8 - largo === 1 ? '' : 'es'}.` }
      : bytes > MAXIMO_BYTES_PASSWORD
        ? { tipo: 'invalido', texto: 'Demasiado larga: con tildes, ñ o emojis caben menos de 72 caracteres.' }
        : { tipo: 'valido', texto: 'Contraseña válida.' };

  async function enviar(evento) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      await api.registrar(datos);
      const nueva = await iniciarSesion(datos.correo, datos.password);
      navegar(inicioSegunRol(nueva.rol), { replace: true });
    } catch (e) {
      setError(e);
      setEnviando(false);
    }
  }

  return (
    <MarcoAcceso titulo="Crear cuenta de estudiante">
      <Alerta error={error} />
      <form onSubmit={enviar} className="formulario">
        <div className="campo">
          <label htmlFor="registro-nombre">Nombre completo</label>
          <input id="registro-nombre" className="entrada" value={datos.nombre} onChange={cambiar('nombre')}
                 maxLength={100} autoComplete="name" required autoFocus />
        </div>
        <div className="campo">
          <label htmlFor="registro-correo">Correo institucional</label>
          <input id="registro-correo" className="entrada" type="email" value={datos.correo}
                 onChange={cambiar('correo')} maxLength={150} autoComplete="email" required />
        </div>
        {/* La ayuda va fuera del label: así el nombre accesible del campo es solo "Contraseña". */}
        <div className="campo">
          <label htmlFor="registro-password">Contraseña</label>
          <input id="registro-password" className="entrada" type="password" value={datos.password}
                 onChange={cambiar('password')}
                 minLength={8} maxLength={72} autoComplete="new-password" required aria-describedby="ayuda-password"
                 aria-invalid={avisoPassword.tipo === 'invalido'} />
          <small id="ayuda-password" className={`ayuda ayuda-${avisoPassword.tipo}`} aria-live="polite">
            {avisoPassword.texto}
          </small>
        </div>
        <button type="submit" className="boton primario" disabled={enviando || avisoPassword.tipo === 'invalido'}>
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>
      <p className="pie-acceso">
        ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
      </p>
    </MarcoAcceso>
  );
}
