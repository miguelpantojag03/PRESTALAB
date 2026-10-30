import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api/cliente.js';
import { useSesion } from '../auth/Sesion.jsx';
import { Alerta } from '../componentes/Comunes.jsx';
import { inicioSegunRol } from '../componentes/Plantilla.jsx';

function MarcoAcceso({ titulo, children }) {
  return (
    <div className="acceso">
      <div className="acceso-tarjeta">
        <div className="acceso-marca">
          <span className="logo" aria-hidden="true">PL</span>
          <div>
            <strong>PrestaLab</strong>
            <span>Préstamo de equipos del laboratorio</span>
          </div>
        </div>
        <h1>{titulo}</h1>
        {children}
      </div>
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
    }
  }

  return (
    <MarcoAcceso titulo="Iniciar sesión">
      {motivoCierre && !error && <Alerta tipo="info">{motivoCierre}</Alerta>}
      <Alerta error={error} />
      <form onSubmit={enviar} className="formulario">
        <label>
          Correo
          <input type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} autoComplete="username"
                 required autoFocus />
        </label>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete="current-password" required />
        </label>
        <button type="submit" className="boton" disabled={enviando}>
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
        <label>
          Nombre completo
          <input value={datos.nombre} onChange={cambiar('nombre')} maxLength={100} autoComplete="name" required
                 autoFocus />
        </label>
        <label>
          Correo institucional
          <input type="email" value={datos.correo} onChange={cambiar('correo')} maxLength={150}
                 autoComplete="email" required />
        </label>
        <label>
          Contraseña
          <input type="password" value={datos.password} onChange={cambiar('password')} minLength={8} maxLength={72}
                 autoComplete="new-password" required />
          <small className="tenue">Entre 8 y 72 caracteres.</small>
        </label>
        <button type="submit" className="boton" disabled={enviando}>
          {enviando ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>
      <p className="pie-acceso">
        ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
      </p>
    </MarcoAcceso>
  );
}
