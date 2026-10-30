import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'motion/react';
import App from './App.jsx';
import { ProveedorSesion } from './auth/Sesion.jsx';
import { ProveedorAvisos } from './componentes/Comunes.jsx';
// Inter, servida desde el propio contenedor: es el respaldo que el DESIGN.md de Notion indica para Notion Sans.
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import './estilos.css';

// reducedMotion="user": con "reducir movimiento" activado en el sistema, Motion quita desplazamientos y escalas
// y conserva los cambios de opacidad, que siguen ayudando a entender qué pasó.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <MotionConfig reducedMotion="user">
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <ProveedorSesion>
          <ProveedorAvisos>
            <App />
          </ProveedorAvisos>
        </ProveedorSesion>
      </BrowserRouter>
    </MotionConfig>
  </StrictMode>,
);
