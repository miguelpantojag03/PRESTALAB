# Pruebas end-to-end de PrestaLab con Postman

| Archivo | Contenido |
|---|---|
| `PrestaLab-API.postman_collection.json` | Colección (formato v2.1.0): 77 peticiones en 10 carpetas que se ejecutan en orden. |
| `PrestaLab-API.local.postman_environment.json` | Entorno local: `baseUrl`, `rootUrl`, `mailpitUrl`, `adminCorreo` y `adminPassword` (tipo `secret`, solo para desarrollo). |

La aplicación sigue siendo 100 % Java 21. Los scripts de la colección están en JavaScript porque es el único
lenguaje que ejecuta Postman. Son artefactos de prueba externos y solo existen en esta carpeta.

## Requisito: la aplicación levantada

```bash
cp .env.example .env          # y pon un PRESTALAB_JWT_SECRET propio: openssl rand -base64 48
mvn -B package -DskipTests
docker compose up -d --build
```

La corrida necesita los tres contenedores de D8. La carpeta 09 consulta Mailpit en `http://localhost:8025`.

### Sin Docker (Windows)

También se puede correr con PostgreSQL 16 y Mailpit instalados en la máquina:

```bash
winget install --id axllent.mailpit -e     # una sola vez
mailpit --smtp 0.0.0.0:1025 --listen 0.0.0.0:8025
```

En otra consola, con PostgreSQL corriendo y una base `prestalab` creada:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/prestalab \
SPRING_DATASOURCE_USERNAME=prestalab SPRING_DATASOURCE_PASSWORD=tu-clave \
PRESTALAB_JWT_SECRET="$(openssl rand -base64 48)" \
java -jar target/prestalab.jar
```

## Ejecutar en Postman (Collection Runner)

1. En Postman, usa **Import** y elige los dos archivos JSON de esta carpeta.
2. Arriba a la derecha, selecciona el entorno **PrestaLab API (local)**.
3. Abre la colección **PrestaLab API**, pulsa **Run** y ejecútala **completa y en orden**, sin cambiar el orden
   de las carpetas.

Cada corrida genera su propio estudiante (`pm.est.{ts}@prestalab.test`) y su propio equipo (`PM-{ts}`).
Por eso se puede repetir sin límite y dos corridas seguidas dan el mismo resultado. Al terminar, el equipo queda
devuelto y desactivado y el estudiante queda activo.

## Ejecutar con Newman

```bash
npx newman run postman/PrestaLab-API.postman_collection.json \
  -e postman/PrestaLab-API.local.postman_environment.json \
  --reporters cli,junit --reporter-junit-export target/newman/resultado.xml
```

En GitHub Actions lo hace el job `e2e-postman` de `.github/workflows/ci.yml`: levanta `docker compose`, espera a
`/v3/api-docs`, ejecuta Newman, publica `resultado.xml` como artefacto y siempre apaga los contenedores.

## Qué cubre cada carpeta

| Carpeta | Cubre |
|---|---|
| 00 Configuración | Genera las variables de la corrida (fechas en America/Bogota). Verifica que `/v3/api-docs` documente los 19 endpoints y `bearerAuth`, y que Swagger UI sea público. |
| 01 Seguridad sin token | 401 `NO_AUTENTICADO` sin token, con la firma alterada y en una ruta inexistente. |
| 02 Autenticación del administrador | RF01: mensaje uniforme ante credenciales inválidas, 400 `VALIDACION` y claims del JWT (`iss`, `sub`, `rol`, `exp - iat = 3600`). |
| 03 Administrador: equipos | RF02 y RN07: crear (201 + `Location`), duplicado (422), validación (400), enum inválido y propiedad desconocida (400), editar, cambiar estado (204), PRESTADO a mano (422), 404, 400 y `DELETE` → 405. |
| 04 Estudiante | RF01, RF03, RF04, RF07 y D6 parte 1: registro, rol colado (400), login, catálogo y filtros, RN02 (400 y 422), P1 y P2 (201), RN01 (422), RN03 (404), historial y cinco accesos prohibidos (403). |
| 05 Administrador: préstamos, panel y estudiantes | RF05, RF06, RF10, RF12, D5 y D6 parte 2: aprobar (200), guarda `[equipo DISPONIBLE]` (409), rechazo sin motivo (400), rechazar (200), estados finales (409), devolución, 404, 400, 405, panel, desactivar estudiante y al administrador (422). |
| 06 Estudiante desactivado | RF12: el login da 401 y una solicitud con el token aún vigente da 422 (decisión 3.12.1). |
| 07 Administrador: reactivación y limpieza | Reactivar al estudiante y desactivar el equipo (RN07); el catálogo deja de mostrarlo. |
| 08 Estudiante: estado final | Login de nuevo e historial: P1 DEVUELTO, P2 RECHAZADO y nada en curso. |
| 09 Correos en Mailpit | RF08: exactamente 5 correos con el asunto `PrestaLab: tu préstamo #{id} está {ESTADO}`, ninguno por las operaciones que fallaron. Reintenta hasta 3 veces. |

Además, el script de la colección verifica en **todas** las respuestas que:
- el tiempo de respuesta sea menor de 2000 ms;
- nunca haya un 500;
- los errores lleguen como `application/problem+json`, con `status`, `codigo`, `title`, `detail` e `instance`;
- los 200 y 201 lleguen como `application/json`;
- ninguna respuesta contenga `passwordHash` ni las contraseñas usadas;
- los DTO de éxito no tengan una propiedad `password`.

Si falta un dato que da un paso anterior (por ejemplo, falló el login del administrador), las peticiones que
dependen de él se omiten con `pm.execution.skipRequest()` y un mensaje en la consola, en lugar de fallar en cadena.

## Qué queda en JUnit y no en Postman

HTTP no permite probar estas reglas sin crear rutas especiales, y la API no las tiene a propósito:

| Regla | Por qué no va por HTTP | Prueba JUnit |
|---|---|---|
| RF09: marcar préstamos vencidos a las 00:00 | Es una tarea programada | `PrestamoServiceImplTest` (`marcarPrestamosVencidos`), `VencimientoSchedulerTest` |
| RF11: recordatorio a las 08:00 | Es una tarea programada | `PrestamoServiceImplTest` (`enviarRecordatorios`), `RecordatorioSchedulerTest`, `EmailNotificadorTest` |
| RN04: con un préstamo VENCIDO no se solicita | Exige que pase el tiempo | `PrestamoServiceImplTest.Solicitar.rn04ConUnPrestamoVencido`, `RepositoriosIntegracionTest` |

## Probar a mano desde Swagger UI

1. Abre `http://localhost:8080/swagger-ui.html`.
2. En **Autenticación → POST /api/v1/auth/login**, pulsa **Try it out** y envía:
   `{"correo": "admin@prestalab.local", "password": "Admin123*"}`.
3. Copia el valor de `token` de la respuesta.
4. Pulsa **Authorize** (arriba a la derecha), pega el token (sin escribir `Bearer`) y confirma.
5. Ya puedes llamar a los endpoints protegidos. El token vence a los 60 minutos; después hay que iniciar sesión otra vez.

> Las credenciales del administrador son solo de desarrollo. Cámbialas antes de cualquier despliegue real.
