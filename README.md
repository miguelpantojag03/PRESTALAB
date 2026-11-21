# PrestaLab

[![CI](https://github.com/miguelpantojag03/PRESTALAB/actions/workflows/ci.yml/badge.svg)](https://github.com/miguelpantojag03/PRESTALAB/actions/workflows/ci.yml)

Sistema de préstamo de equipos del laboratorio universitario. Tiene dos partes:

- **Backend:** API REST con JSON sobre HTTP, autenticación stateless con JWT y datos en MongoDB. No publica
  Swagger ni OpenAPI: la API se documenta en este README y en la [colección de Postman](postman/README.md). Ver el
  [ADR 0008](docs/adr/0008-mongodb-y-sin-swagger.md).
- **Frontend:** aplicación web en React (carpeta [frontend/](frontend/README.md)) que consume esa API. Ver el
  [ADR 0007](docs/adr/0007-frontend-react-en-contenedor-propio.md).

- **Estudiante:** consulta el catálogo, solicita préstamos y revisa su historial.
- **Administrador:** aprueba o rechaza solicitudes, registra devoluciones y gestiona equipos y estudiantes.
- **El sistema:**
  - envía un correo en cada cambio de estado (HTML con versión de texto plano);
  - marca los préstamos vencidos a las 00:00;
  - a las 08:00 recuerda los préstamos que vencen al día siguiente.

Java 21 · Spring Boot 3.5 · Spring Security 6 (OAuth2 Resource Server, JWT HS256) · Spring Data MongoDB ·
MongoDB 7 · Docker Compose · Thymeleaf (solo para las plantillas de correo) · React 18 · Vite · nginx.

## Requisitos

- JDK 21 y Maven 3.9
- Docker con Docker Compose (para levantar el sistema y para las pruebas con Testcontainers)
- Node.js 20 o superior, solo para desarrollar el frontend fuera de Docker

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
| **Aplicación web (frontend)** | **http://localhost:3000** |
| API | http://localhost:8080/api/v1 |
| MongoDB (con Compass o mongosh) | mongodb://localhost:27017 (usuario y clave de `.env`, `authSource=admin`) |
| Mailpit (correos enviados) | http://localhost:8025 |

## Obtener un token

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"admin@prestalab.local","password":"Admin123*"}'
# {"token":"eyJ...","tipo":"Bearer","expiraEnSegundos":3600,"rol":"ADMIN"}

curl -s http://localhost:8080/api/v1/dashboard/resumen -H "Authorization: Bearer eyJ..."
```

**En Postman:** importa la colección y el entorno de `postman/`. La petición de login guarda el token y las demás lo
envían solas en la cabecera `Authorization`.

El token vence a los 60 minutos. No hay refresh tokens: al vencer, se inicia sesión otra vez.

## Credenciales de desarrollo

| Rol | Correo | Contraseña |
|---|---|---|
| ADMIN | `admin@prestalab.local` | `Admin123*` |

Los estudiantes se registran con `POST /api/v1/auth/registro`, que siempre crea una cuenta de ESTUDIANTE.

> **Advertencia:** estas credenciales solo sirven para desarrollo. Antes de ir a producción:
> - cambia la contraseña del administrador (o reemplaza `HASH_ADMIN` en `service/DatosIniciales.java` por uno nuevo);
> - usa contraseñas reales y un secreto JWT propio en `.env`, que nunca se versiona;
> - no publiques el puerto 27017 de MongoDB fuera de la máquina.

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
| `prestalab-web` | build de `frontend/Dockerfile` (Node 20 compila; `nginx-unprivileged` sirve y reenvía `/api`) | 3000:8080 |
| `prestalab-app` | build del `Dockerfile` (`eclipse-temurin:21-jre`, usuario no root) | 8080:8080 |
| `prestalab-db` | `mongo:7.0`, volumen `mongodata:/data/db` | 27017:27017 |
| `prestalab-mail` | `axllent/mailpit` | 1025:1025 (SMTP), 8025:8025 (web) |

Son cuatro contenedores: el frontend amplía los tres originales de D8 (ver el ADR 0007). Todos están en la red
`prestalab-net` (bridge). El navegador solo habla con `prestalab-web`, que reenvía `/api` a `prestalab-app`. Así hay
un solo origen y el backend no necesita CORS. Variables de entorno de `prestalab-app`: `SPRING_DATA_MONGODB_URI`
(armada con `MONGO_USER` y `MONGO_PASSWORD` de `.env`), `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` y
`PRESTALAB_JWT_SECRET`.

## Pruebas

| Tipo | Clases | Necesita Docker |
|---|---|---|
| Dominio (D4, D5) | `PrestamoTest`, `EquipoTest`, `UsuarioTest` | No |
| Servicios con Mockito (secuencia de D6 con `InOrder`, claims del JWT) | `*ServiceImplTest` | No |
| Notificación (correo HTML multipart) y tareas programadas | `EmailNotificadorTest`, `VencimientoSchedulerTest`, `RecordatorioSchedulerTest` | No |
| Capa REST (19 endpoints, matriz de seguridad, ProblemDetail) | `ControladoresRestTest` | No |
| Arquitectura (las 15 dependencias de D7, lista blanca de clases, solo `@RestController`) | `ArquitecturaTest`, `FidelidadDiccionarioTest` | No |
| Datos iniciales (hash BCrypt real del administrador, equipos de ejemplo) y `.env.example` | `DatosInicialesTest` | No |
| Persistencia con MongoDB 7: consultas, índices únicos (incluido el parcial), escrituras condicionales y candado | `RepositoriosIntegracionTest` | Sí |
| API completa: flujo de D6, RF12, peticiones simultáneas y que Swagger ya no existe | `ApiIntegracionTest` | Sí |

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

`.github/workflows/ci.yml` tiene tres jobs y no usa secretos del repositorio:

| Job | Qué hace |
|---|---|
| `verify` | `mvn -B verify` (Ubuntu, Temurin 21, caché de Maven). Corre todas las pruebas, incluidas las de Testcontainers, y publica el reporte de JaCoCo como artefacto `reporte-jacoco`. |
| `frontend` | En paralelo con `verify`: `npm ci`, las pruebas de Vitest y `npm run build`. |
| `e2e-postman` | Corre después de los otros dos. Empaqueta el jar, genera un secreto JWT en el propio job, levanta los cuatro contenedores y comprueba que el frontend responde en el puerto 3000 y reenvía la API. Después ejecuta Newman y publica el reporte JUnit como `reporte-newman`. Siempre apaga los contenedores. |

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
- **Datos iniciales:** `DatosIniciales` siembra al arrancar el administrador y 9 equipos DISPONIBLES y
  EN_MANTENIMIENTO, pero ninguno PRESTADO: un equipo PRESTADO sin préstamo asociado nunca podría volver a
  DISPONIBLE. Solo siembra lo que falta, así que reiniciar la aplicación no duplica nada.
- **Ids numéricos:** MongoDB genera ids `ObjectId`, pero `GeneradorIds` asigna a cada documento nuevo un `Long`
  consecutivo (colección `contadores`, incremento atómico). La API, el frontend y Postman siguen viendo ids 1, 2, 3…
- **Peticiones simultáneas:** el servicio verifica las reglas antes de guardar, y MongoDB detiene a la segunda de
  dos peticiones que pasaron esa verificación al mismo tiempo (`OperacionesAtomicas`, ADR 0008). El error llega
  antes de enviar el correo.
  - Dos operaciones sobre el mismo préstamo (por ejemplo, aprobar y rechazar): cada cambio de estado es una
    escritura condicional que solo reemplaza el documento si sigue en el estado de origen. La segunda responde 409.
  - Aprobar toma primero el equipo (solo si sigue DISPONIBLE y activo) y después el préstamo; si el préstamo cambió
    entretanto, devuelve el equipo a DISPONIBLE. Así el equipo nunca queda PRESTADO con un préstamo rechazado.
  - Dos aprobaciones del mismo equipo: la segunda no consigue tomar el equipo, y además el índice único parcial
    `ux_prestamos_equipo_en_curso` impide dos préstamos APROBADOS o VENCIDOS del mismo equipo. Responde 422.
  - RN01 con solicitudes simultáneas: cada estudiante tiene un candado (colección `candados`); sus solicitudes pasan
    de a una y cuentan bien. Las que sobran responden 422.
- **Contraseñas:** BCrypt admite 72 bytes, no 72 caracteres. Una contraseña con tildes, ñ o emojis que pase de
  72 bytes se rechaza con 422 en lugar de provocar un 500.
- **Recordatorios (RF11):** `enviarRecordatorios()` es de solo lectura y llama a `notificarRecordatorio`, nunca a
  `notificarCambioEstado`. Un recordatorio no cambia el estado del préstamo, así que D5 no cambia.

## Estructura

```
src/main/java/com/prestalab
├── PrestalabApplication.java   (@EnableScheduling)
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
├── repository/        UsuarioRepository, EquipoRepository, PrestamoRepository (MongoRepository),
│                      GeneradorIds, OperacionesAtomicas
├── scheduler/         VencimientoScheduler, RecordatorioScheduler
├── service/           *Service + *ServiceImpl (Usuario, Equipo, Prestamo, Dashboard), DatosIniciales
└── web/error/         GlobalExceptionHandler
src/main/resources
├── application.yml
└── templates/correo/  cambio-estado.html, recordatorio.html
postman/               colección, entorno y README de las pruebas end-to-end
docs/adr/              registro de decisiones de arquitectura (ADR 0001 a 0008)
frontend/              aplicación web React + Vite, su Dockerfile y la configuración de nginx
```
