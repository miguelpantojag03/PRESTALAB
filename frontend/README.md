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

El diseño combina tres fuentes:

- **El aspecto** sigue el [DESIGN.md de Notion](https://github.com/VoltAgent/awesome-design-md/tree/main/design-md/notion)
  de la colección awesome-design-md. De ahí salen el lienzo blanco con la barra lateral `#f6f5f4`, los bordes cálidos
  `#e5e3df` y la tinta `#37352f`; el violeta `#5645d4` solo en la acción principal de cada pantalla; los botones
  rectangulares de 8px y las tarjetas de 12px; las etiquetas pastel como las propiedades de sus bases de datos; el
  ícono de página, los callouts y las pestañas en píldora; y la banda azul marino con puntos de colores en el acceso.
  Se toma el sistema de diseño, no la marca: PrestaLab conserva su nombre y su marca.
- **La estructura** de cada pantalla viene del trabajo anterior con tasteskill: cada pantalla responde una pregunta
  primero (tabla de abajo), hay estados de carga, vacío y error, y "El estante" es el elemento propio del producto.
- **El movimiento** sigue los principios de Apple (*Designing Fluid Interfaces*, WWDC), con
  [Motion](https://motion.dev): resortes interrumpibles, gestos 1:1 y proyección de impulso.

Ajustes propios sobre el DESIGN.md, anotados en `src/estilos.css`:

- "Notion Sans" es privada, así que se usa **Inter**, el respaldo que indica el propio documento. Se sirve desde el
  contenedor.
- El texto de las etiquetas usa el tono profundo de cada familia (por ejemplo, `#1c6b31` sobre menta) para llegar
  a 4.5:1. El verde del documento, `#1aae39` sobre menta, da 2.6:1.
- El documento no trae **modo oscuro** (lo dice en "Known Gaps"). Se extrapola de los grises cálidos de la app de
  Notion.

**Cada pantalla responde una pregunta primero:**

| Pantalla | Pregunta | Qué es lo más grande |
|---|---|---|
| Panel | ¿Qué hay que atender hoy en el mostrador? | La cola de solicitudes, con Aprobar y Rechazar en la fila; debajo del título, el hallazgo del día calculado de los datos |
| Préstamos | ¿Qué préstamo necesita una acción? | Tarjetas de conteo (Por aprobar, Por devolver, Atrasados) que filtran la lista |
| Equipos | ¿Dónde está cada equipo? | La columna "Dónde está": quién lo tiene y cuándo vuelve |
| Estudiantes | ¿Quién puede pedir equipos? | Préstamos en curso de cada estudiante, de los 2 permitidos |
| Catálogo | ¿Qué me puedo llevar hoy? | El cupo del estudiante (RN01): si ya tiene 2 préstamos, lo ve antes de intentar |
| Mis préstamos | ¿Qué tengo y cuándo lo devuelvo? | La próxima devolución, en palabras ("En 3 días") |

**El estante**, en el Panel, es el elemento propio de PrestaLab. Cada equipo activo es una celda con su código,
agrupada por categoría. Los indicadores de estado son tarjetas pastel (menta, cielo y durazno) que funcionan como
pestañas: las celdas del estado elegido toman el color de esa propiedad, y el título de cada celda dice quién tiene
el equipo y hasta cuándo.

| Principio | Cómo se aplica |
|---|---|
| Datos honestos | Una cifra que se muestra dos veces sale de la misma variable: los indicadores del estante se cuentan de la misma lista que lo dibuja. Los ceros se muestran como ceros y lo que falta, como una raya. |
| Estados | Cargando: barras grises del tamaño real, sin que la disposición salte. Error: dentro del bloque que falló, con Reintentar. Vacío: el bloque conserva su marco y una oración. |
| El rojo es una señal | Solo marca lo atrasado. Las acciones de fila van en tinta. |
| Interrumpible | Si un diálogo se reabre mientras sale, se crea uno nuevo (`useDialogo`) y el que sale queda `inert`. |
| Gestos | En el celular, el diálogo es una hoja inferior que se arrastra 1:1 y se descarta con un gesto rápido. Los avisos se descartan deslizándolos. |
| Agency | Las acciones reversibles ofrecen Deshacer en el aviso. Solo lo irreversible (desactivar un equipo) pide confirmación. |
| Celular | La barra lateral se pliega a una barra de 48px con menú; las tablas pasan a listas de dos líneas sin perder campos; los avisos aparecen arriba. |
| Accesibilidad | Modo claro y oscuro, *reducir movimiento* y *aumentar contraste*. La ayuda de cada campo va fuera del `<label>`, enlazada con `aria-describedby`. |

**Verificación:** se revisaron capturas de cada pantalla en modo claro y oscuro, a 1440, 768 y 390px. El inspector
de tasteskill se usó solo para medir contraste y desbordes (sus reglas de estilo, como la prohibición del violeta,
no aplican a este sistema): sin problemas en las seis pantallas.

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
