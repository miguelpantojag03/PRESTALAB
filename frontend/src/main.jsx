import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'motion/react';
import App from './App.jsx';
import { ProveedorSesion } from './auth/Sesion.jsx';
import { ProveedorAvisos } from './componentes/Comunes.jsx';
// IBM Plex, servida desde el propio contenedor: la tipografía asignada por el estilo se carga de verdad.
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-mono/400.css';
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
