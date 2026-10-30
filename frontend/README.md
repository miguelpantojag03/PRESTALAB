# PrestaLab: frontend

Aplicación web en React 18 + Vite que consume la API `/api/v1` del backend. Las decisiones de diseño están en el
[ADR 0007](../docs/adr/0007-frontend-react-en-contenedor-propio.md).

## Pantallas

| Rol | Ruta | Qué hace | Requisito |
|---|---|---|---|
| Todos | `/login`, `/registro` | Iniciar sesión y crear una cuenta de estudiante | RF01 |
| Estudiante | `/catalogo` | Catálogo con filtro por categoría y disponibilidad; solicitar un préstamo de hasta 7 días | RF03, RF04 |
| Estudiante | `/mis-prestamos` | Historial con estado, fechas y motivo de rechazo | RF07 |
| Administrador | `/admin` | Indicadores del laboratorio y solicitudes pendientes | RF10 |
| Administrador | `/admin/prestamos` | Aprobar o rechazar (con motivo) y registrar devoluciones; resalta los atrasados | RF05, RF06 |
| Administrador | `/admin/equipos` | Crear, editar, mandar a mantenimiento, marcar disponible y desactivar | RF02, RN07 |
| Administrador | `/admin/estudiantes` | Activar y desactivar cuentas | RF12 |

## Diseño de la interfaz

La interfaz sigue los principios de diseño de Apple (*Designing Fluid Interfaces* y *The Details of UI
Typography*, de la WWDC), llevados a la web con [Motion](https://motion.dev). Los resortes están en
`src/util/movimiento.js` y el sistema visual en `src/estilos.css`.

| Principio | Cómo se aplica |
|---|---|
| Respuesta inmediata | Los botones reaccionan al presionar (`:active`), no al soltar. |
| Interrumpible | Todo lo que se mueve usa resortes, que parten del valor que está en pantalla. Si un diálogo se reabre mientras sale, se crea uno nuevo (`useDialogo`) y el que sale queda `inert`. |
| Resortes, no duraciones | Críticamente amortiguados por defecto (sin rebote). Solo rebotan cuando el gesto trae impulso: soltar una hoja o deslizar un aviso. |
| Manipulación directa | En el celular, el diálogo es una hoja inferior que se arrastra 1:1 desde su barra, con resistencia elástica hacia arriba. |
| Proyección de impulso | Al soltar la hoja o un aviso se proyecta dónde terminaría el gesto (fórmula de Apple, desaceleración 0.998): un gesto rápido descarta aunque haya recorrido poco. |
| Consistencia espacial | El diálogo nace del botón que lo abrió y vuelve a él al cerrarse. Los avisos entran y salen por el mismo borde, o hacia donde los empujó el dedo. La píldora del menú y el indicador de pestañas se deslizan entre opciones. |
| Materiales | La barra superior y los avisos son capas translúcidas (`backdrop-filter`). El borde de la barra solo aparece cuando hay contenido debajo. El velo oscurece el fondo solo en tareas modales. |
| Agency | Las acciones reversibles (desactivar una cuenta, cambiar el estado de un equipo) ofrecen Deshacer en el aviso. Solo lo irreversible (desactivar un equipo) pide confirmación. |
| Validación en línea | La contraseña se valida mientras se escribe, incluido el límite de 72 bytes de BCrypt; la fecha muestra su rango permitido. |
| Tipografía | Fuente del sistema, tracking según el tamaño (−0.022em en títulos y +0.006em en texto pequeño), interlineado inverso al tamaño, cifras tabulares y medidas en `rem`. |
| Flexibilidad | En el celular las tablas se convierten en tarjetas "etiqueta: valor" y los avisos pasan arriba para no tapar las hojas. |
| Accesibilidad | Se respetan *reducir movimiento* (sin desplazamientos ni sacudida), *reducir transparencia* (materiales sólidos) y *aumentar contraste* (bordes definidos). La ayuda de cada campo va fuera del `<label>`, enlazada con `aria-describedby`. |

## Uso

Con Docker, desde la raíz del proyecto:

```bash
docker compose up -d --build     # la aplicación queda en http://localhost:3000
```

En desarrollo, con el backend corriendo en `localhost:8080`:

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173; Vite reenvía /api al backend
npm test          # pruebas con Vitest
npm run build     # genera dist/
```

Para apuntar a otro backend: `PRESTALAB_API_URL=http://otro-host:8080 npm run dev`.

## Estructura

```
src/
├── api/cliente.js        los 19 endpoints; convierte los ProblemDetail en ErrorApi
├── auth/                 sesión (JWT en sessionStorage) y lectura del payload
├── componentes/          plantilla con menú por rol, rutas protegidas, alertas, insignias, diálogos
├── paginas/              Acceso, Estudiante y Admin
├── util/                 fechas en America/Bogota y carga de datos
└── pruebas/              Vitest + Testing Library
```

La seguridad la aplica el backend. El frontend solo oculta lo que el rol no puede usar; si alguien fuerza una
ruta o una petición, la API responde 401 o 403.
