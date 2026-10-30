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
