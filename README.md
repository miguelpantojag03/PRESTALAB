# PrestaLab

[![CI](https://github.com/miguelpantojag03/PRESTALAB/actions/workflows/ci.yml/badge.svg)](https://github.com/miguelpantojag03/PRESTALAB/actions/workflows/ci.yml)

API REST de préstamo de equipos del laboratorio universitario. JSON sobre HTTP, autenticación stateless con JWT
y documentación con Swagger UI. No tiene interfaz web propia: se usa desde Swagger UI, Postman o cualquier cliente HTTP.

- **Estudiante:** consulta el catálogo, solicita préstamos y revisa su historial.
- **Administrador:** aprueba o rechaza solicitudes, registra devoluciones y gestiona equipos y estudiantes.
- **El sistema:**
  - envía un correo en cada cambio de estado (HTML con versión de texto plano);
  - marca los préstamos vencidos a las 00:00;
  - a las 08:00 recuerda los préstamos que vencen al día siguiente.

Java 21 · Spring Boot 3.5 · Spring Security 6 (OAuth2 Resource Server, JWT HS256) · Spring Data JPA ·
springdoc-openapi · PostgreSQL 16 · Flyway · Docker Compose · Thymeleaf (solo para las plantillas de correo).

## Requisitos

- JDK 21 y Maven 3.9
- Docker con Docker Compose (para levantar el sistema y para las pruebas con Testcontainers)

## Levantar todo (D7 y D8)

```bash
cp .env.example .env                 # cambia las contraseñas y pon un secreto JWT propio:
openssl rand -base64 48              # → pégalo en PRESTALAB_JWT_SECRET (mínimo 32 bytes)
mvn test                             # pruebas + cobertura mínima del 80 % en domain y service
mvn package                          # genera target/prestalab.jar
docker build -t prestalab-app .
docker compose up -d                 # (o directamente: docker compose up --build -d)
```

Si `PRESTALAB_JWT_SECRET` falta o tiene menos de 32 bytes, la aplicación no arranca y muestra un mensaje claro.

## URLs

| Servicio | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Especificación OpenAPI | http://localhost:8080/v3/api-docs |
| Mailpit (correos enviados) | http://localhost:8025 |

## Obtener un token

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"admin@prestalab.local","password":"Admin123*"}'
# {"token":"eyJ...","tipo":"Bearer","expiraEnSegundos":3600,"rol":"ADMIN"}

curl -s http://localhost:8080/api/v1/dashboard/resumen -H "Authorization: Bearer eyJ..."
```

**En Swagger UI:**
1. Ejecuta `POST /api/v1/auth/login` con "Try it out".
2. Copia el `token` de la respuesta.
3. Pulsa **Authorize**, pega el token (sin escribir `Bearer`) y confirma.

El token vence a los 60 minutos. No hay refresh tokens: al vencer, se inicia sesión otra vez.

## Credenciales de desarrollo

| Rol | Correo | Contraseña |
|---|---|---|
| ADMIN | `admin@prestalab.local` | `Admin123*` |

Los estudiantes se registran con `POST /api/v1/auth/registro`, que siempre crea una cuenta de ESTUDIANTE.

> **Advertencia:** estas credenciales solo sirven para desarrollo. Antes de ir a producción:
> - cambia la contraseña del administrador (o reemplaza el hash de `V2__datos_iniciales.sql` por uno nuevo);
> - usa contraseñas reales y un secreto JWT propio en `.env`, que nunca se versiona;
> - apaga Swagger con `SPRINGDOC_API_DOCS_ENABLED=false`.

## Endpoints

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `POST /api/v1/auth/registro` | público | 201 UsuarioDTO |
| `POST /api/v1/auth/login` | público | 200 TokenDTO |
| `GET /api/v1/equipos?categoria=&soloDisponibles=` | ESTUDIANTE, ADMIN | 200 [EquipoDTO] |
| `GET /api/v1/equipos/{id}` | ESTUDIANTE, ADMIN | 200 EquipoDTO |
| `POST /api/v1/equipos` | ADMIN | 201 EquipoDTO + `Location` |
| `PUT /api/v1/equipos/{id}` | ADMIN | 200 EquipoDTO |
| `PATCH /api/v1/equipos/{id}/estado` | ADMIN | 204 |
| `POST /api/v1/equipos/{id}/desactivar` | ADMIN | 204 |
| `POST /api/v1/prestamos` | ESTUDIANTE | 201 PrestamoDTO |
| `GET /api/v1/prestamos/mios` | ESTUDIANTE | 200 [PrestamoDTO] |
| `GET /api/v1/prestamos/pendientes` | ADMIN | 200 [PrestamoDTO] |
| `GET /api/v1/prestamos/por-devolver` | ADMIN | 200 [PrestamoDTO] |
| `POST /api/v1/prestamos/{id}/aprobar` | ADMIN | 200 PrestamoDTO |
| `POST /api/v1/prestamos/{id}/rechazar` (cuerpo `{ "motivo" }`) | ADMIN | 200 PrestamoDTO |
| `POST /api/v1/prestamos/{id}/devolver` | ADMIN | 200 PrestamoDTO |
| `GET /api/v1/dashboard/resumen` | ADMIN | 200 ResumenDashboardDTO |
| `GET /api/v1/usuarios/estudiantes` | ADMIN | 200 [UsuarioDTO] |
| `POST /api/v1/usuarios/{id}/activar` · `/desactivar` | ADMIN | 204 |

Aprobar, rechazar, devolver, activar y desactivar son transiciones con reglas (D5), por eso son `POST /{id}/accion`
y no `PATCH` genéricos. Equipos no tiene `DELETE`: un equipo con historial nunca se elimina, solo se desactiva (RN07).

**Errores:** siempre llegan como `application/problem+json` (RFC 9457) con `type`, `title`, `status`, `detail`,
`instance`, `codigo` y `timestamp`.

| Status | `codigo` | Cuándo |
|---|---|---|
| 400 | `VALIDACION` (con `errores: {campo: mensaje}`) o `PETICION_INVALIDA` | Datos inválidos, JSON mal formado, enum inválido o propiedad desconocida |
| 401 | `NO_AUTENTICADO` o `CREDENCIALES_INVALIDAS` | Sin token, token inválido o vencido; login fallido |
| 403 | `ACCESO_DENEGADO` | Rol incorrecto |
| 404 | `RECURSO_NO_ENCONTRADO` | Id inexistente |
| 405 | `METODO_NO_PERMITIDO` | Método no soportado (por ejemplo `DELETE` sobre equipos) |
| 409 | `TRANSICION_INVALIDA` | Transición no permitida por D5 |
| 422 | `REGLA_NEGOCIO` | Regla de negocio incumplida (RN) o cuenta desactivada |
| 500 | `ERROR_INTERNO` | Error inesperado, sin detalles internos |

### Gestión de estudiantes (RF12)

Un estudiante desactivado no puede iniciar sesión: recibe el mismo 401 genérico, "Correo o contraseña
incorrectos". Tampoco puede solicitar préstamos aunque su token siga vigente: recibe 422 "Tu cuenta está
desactivada". Sus préstamos no cambian. Las cuentas de administrador no se pueden desactivar.

## Despliegue (D8)

| Contenedor | Imagen | Puertos |
|---|---|---|
| `prestalab-app` | build del `Dockerfile` (`eclipse-temurin:21-jre`, usuario no root) | 8080:8080 |
| `prestalab-db` | `postgres:16`, volumen `pgdata:/var/lib/postgresql/data` | 5432:5432 |
| `prestalab-mail` | `axllent/mailpit` | 1025:1025 (SMTP), 8025:8025 (web) |

Todos están en la red `prestalab-net` (bridge). Variables de entorno de `prestalab-app`:
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_MAIL_HOST`,
`SPRING_MAIL_PORT` y `PRESTALAB_JWT_SECRET`.

## Pruebas

| Tipo | Clases | Necesita Docker |
|---|---|---|
| Dominio (D4, D5) | `PrestamoTest`, `EquipoTest`, `UsuarioTest` | No |
| Servicios con Mockito (secuencia de D6 con `InOrder`, claims del JWT) | `*ServiceImplTest` | No |
| Notificación (correo HTML multipart) y tareas programadas | `EmailNotificadorTest`, `VencimientoSchedulerTest`, `RecordatorioSchedulerTest` | No |
| Capa REST (19 endpoints, matriz de seguridad, ProblemDetail) | `ControladoresRestTest` | No |
| Arquitectura (las 15 dependencias de D7, lista blanca de clases, solo `@RestController`) | `ArquitecturaTest`, `FidelidadDiccionarioTest` | No |
| Hash BCrypt real del administrador | `DatosInicialesTest` | No |
| Persistencia con PostgreSQL 16 | `RepositoriosIntegracionTest` | Sí |
| API completa: flujo de D6, RF12 y OpenAPI | `ApiIntegracionTest` | Sí |

Las pruebas que necesitan Docker usan `@Testcontainers(disabledWithoutDocker = true)`: sin Docker aparecen como
omitidas (*skipped*), no como fallidas. El reporte de cobertura queda en `target/site/jacoco/index.html`.

### Pruebas end-to-end con Postman

La colección `postman/PrestaLab-API.postman_collection.json` recorre la API de punta a punta contra los
contenedores reales. Tiene 77 peticiones en 10 carpetas que cubren los 19 endpoints, la matriz de seguridad, todos
los códigos de error, cada transición de D5 y los correos en Mailpit. Crea sus propios datos, así que se puede
repetir sin límite.

```bash
docker compose up -d --build
npx newman run postman/PrestaLab-API.postman_collection.json \
  -e postman/PrestaLab-API.local.postman_environment.json \
  --reporters cli,junit --reporter-junit-export target/newman/resultado.xml
```

Para importarla en Postman, ver qué cubre cada carpeta y qué queda en JUnit (RF09, RF11 y RN04), consulta
[postman/README.md](postman/README.md).

### Integración continua

`.github/workflows/ci.yml` tiene dos jobs y no usa secretos del repositorio:

| Job | Qué hace |
|---|---|
| `verify` | `mvn -B verify` (Ubuntu, Temurin 21, caché de Maven). Corre todas las pruebas, incluidas las de Testcontainers, y publica el reporte de JaCoCo como artefacto `reporte-jacoco`. |
| `e2e-postman` | Corre después de `verify`. Empaqueta el jar, genera un secreto JWT en el propio job, levanta `docker compose`, ejecuta Newman y publica el reporte JUnit como `reporte-newman`. Siempre apaga los contenedores. |

## Decisiones de implementación

Las decisiones de arquitectura, con sus alternativas y consecuencias, están en [docs/adr/](docs/adr/README.md).
Esta sección resume los detalles de implementación.

- **Zona horaria:** toda fecha "de hoy" usa `ZoneId.of("America/Bogota")`. Además, `PrestalabApplication` fija la
  zona por defecto de la JVM en America/Bogota. Así `@FutureOrPresent` no falla entre las 19:00 y las 24:00
  cuando el contenedor corre en UTC.
- **JWT:**
  - HS256 con el secreto de `PRESTALAB_JWT_SECRET`, con los claims `iss=prestalab`, `sub` (id del usuario),
    `rol`, `nombre`, `iat` y `exp` (60 minutos).
  - Se valida la firma, la expiración y el emisor.
  - Los controladores toman el usuario del `sub`, nunca del cuerpo ni de la URL.
  - Se usa el soporte JWT de Spring Security (Nimbus), sin librerías extra.
- **Login uniforme:** correo inexistente, contraseña incorrecta y cuenta inactiva dan el mismo 401. Si el correo
  no existe, la contraseña se compara igual contra un hash BCrypt fijo, para que el tiempo de respuesta no revele
  qué correos están registrados.
- **Sin CSRF ni sesión:** la API es stateless y el token viaja en la cabecera `Authorization`, no en una cookie.
- **Autorización en dos capas:** reglas por URL en `SecurityConfig` y `@PreAuthorize` en cada endpoint.
- **Datos iniciales:** V2 siembra equipos DISPONIBLE y EN_MANTENIMIENTO, pero ninguno PRESTADO. Un equipo
  PRESTADO sin préstamo asociado nunca podría volver a DISPONIBLE. Los scripts de Flyway no se editan.
- **Peticiones simultáneas:** el servicio verifica las reglas antes de guardar, y PostgreSQL detiene a la segunda
  de dos peticiones que pasaron esa verificación al mismo tiempo. Los cambios se guardan con `saveAndFlush`, así que
  el error llega antes de enviar el correo.
  - Dos aprobaciones del mismo equipo: el índice único parcial `ux_prestamos_equipo_en_curso` (V1). La segunda
    responde 422.
  - RN01 con solicitudes simultáneas: el trigger `tg_prestamos_rn01` (V3) toma un candado por usuario y vuelve a
    contar. Las que sobran responden 422.
  - Dos operaciones sobre el mismo préstamo (por ejemplo, aprobar y rechazar): el trigger `tg_prestamos_transicion`
    (V3) solo admite las transiciones de D5. La segunda responde 409, así que el equipo nunca queda PRESTADO con
    un préstamo rechazado.
- **Contraseñas:** BCrypt admite 72 bytes, no 72 caracteres. Una contraseña con tildes, ñ o emojis que pase de
  72 bytes se rechaza con 422 en lugar de provocar un 500.
- **Recordatorios (RF11):** `enviarRecordatorios()` es de solo lectura y llama a `notificarRecordatorio`, nunca a
  `notificarCambioEstado`. Un recordatorio no cambia el estado del préstamo, así que D5 no cambia.

## Estructura

```
src/main/java/com/prestalab
├── PrestalabApplication.java   (@EnableScheduling, @OpenAPIDefinition, @SecurityScheme bearerAuth)
├── config/            SecurityConfig
├── controller/        AuthController, EquipoController, PrestamoController, DashboardController, UsuarioController
├── domain/
│   ├── enums/         Rol, CategoriaEquipo, EstadoEquipo, EstadoPrestamo
│   ├── exception/     ReglaNegocioException, TransicionInvalidaException, RecursoNoEncontradoException
│   └── model/         Usuario, Equipo, Prestamo
├── dto/               RegistroUsuarioDTO, LoginDTO, TokenDTO, UsuarioDTO, EquipoDTO, EquipoFormDTO,
│                      CambioEstadoEquipoDTO, SolicitudPrestamoDTO, RechazoPrestamoDTO, PrestamoDTO,
│                      ResumenDashboardDTO
├── mapper/            UsuarioMapper, EquipoMapper, PrestamoMapper
├── notification/      Notificador, EmailNotificador
├── repository/        UsuarioRepository, EquipoRepository, PrestamoRepository
├── scheduler/         VencimientoScheduler, RecordatorioScheduler
├── service/           *Service + *ServiceImpl (Usuario, Equipo, Prestamo, Dashboard)
└── web/error/         GlobalExceptionHandler
src/main/resources
├── application.yml
├── db/migration/      V1__esquema.sql, V2__datos_iniciales.sql, V3__reglas_concurrentes.sql
└── templates/correo/  cambio-estado.html, recordatorio.html
postman/               colección, entorno y README de las pruebas end-to-end
docs/adr/              registro de decisiones de arquitectura (ADR 0001 a 0006)
```
