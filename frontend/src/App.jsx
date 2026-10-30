import { Navigate, Route, Routes } from 'react-router-dom';
import { useSesion } from './auth/Sesion.jsx';
import { Plantilla, RutaProtegida, inicioSegunRol } from './componentes/Plantilla.jsx';
import { IniciarSesion, Registro } from './paginas/Acceso.jsx';
import { Equipos, Estudiantes, Panel, Prestamos } from './paginas/Admin.jsx';
import { Catalogo, MisPrestamos } from './paginas/Estudiante.jsx';

function Inicio() {
  const { sesion } = useSesion();
  return <Navigate to={sesion ? inicioSegunRol(sesion.rol) : '/login'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<IniciarSesion />} />
      <Route path="/registro" element={<Registro />} />
      <Route element={<RutaProtegida rol="ESTUDIANTE" />}>
        <Route element={<Plantilla />}>
          <Route path="/catalogo" element={<Catalogo />} />
          <Route path="/mis-prestamos" element={<MisPrestamos />} />
        </Route>
      </Route>
      <Route element={<RutaProtegida rol="ADMIN" />}>
        <Route element={<Plantilla />}>
          <Route path="/admin" element={<Panel />} />
          <Route path="/admin/prestamos" element={<Prestamos />} />
          <Route path="/admin/equipos" element={<Equipos />} />
          <Route path="/admin/estudiantes" element={<Estudiantes />} />
        </Route>
      </Route>
      <Route path="*" element={<Inicio />} />
    </Routes>
  );
}
