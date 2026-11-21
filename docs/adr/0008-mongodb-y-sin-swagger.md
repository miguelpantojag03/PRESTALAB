# ADR 0008: MongoDB en lugar de PostgreSQL y una API sin Swagger

**Estado:** Aceptada. Reemplaza el mecanismo de concurrencia del ADR 0004, el nivel 4 de pruebas del ADR 0006 y
la base de datos de D8.

## Contexto

El profesor pidió dos cambios después de entregar la v3:

1. La base de datos debe ser **MongoDB**, no PostgreSQL.
2. La API **no debe usar Swagger**.

Hasta ese momento:

- PostgreSQL 16 con Flyway guardaba los datos. La migración V2 sembraba los datos iniciales.
- Varias reglas las hacía cumplir la base de datos aunque llegaran peticiones simultáneas: el índice único parcial
  `ux_prestamos_equipo_en_curso` (V1), el trigger `tg_prestamos_transicion` (V3, transiciones de D5) y el trigger
  `tg_prestamos_rn01` (V3, RN01 con un candado por usuario).
- springdoc publicaba Swagger UI en `/swagger-ui.html` y el contrato OpenAPI en `/v3/api-docs`.

Había que hacer los dos cambios sin romper lo que ya estaba verificado: los 19 endpoints, los DTO, los códigos de
error, el diccionario de D4 y las pruebas de concurrencia.

## Decisión

### MongoDB

- Se usa **Spring Data MongoDB** con **MongoDB 7**. Las colecciones son `usuarios`, `equipos` y `prestamos`, más
  dos de apoyo: `contadores` y `candados`.
- Las entidades pasan de `@Entity` a `@Document` **sin atributos nuevos**: D4 no cambia.
- El préstamo **referencia** a su usuario y a su equipo con `@DocumentReference`: guarda su id y los carga al leer.
  No se embeben, porque el estado del equipo debe existir en un solo lugar.
- Los ids siguen siendo **`Long` consecutivos**. `GeneradorIds` los asigna antes de insertar, con un contador
  atómico por colección. Así la API, el frontend y Postman siguen viendo ids 1, 2, 3…
- Los índices se declaran en las entidades y se crean al arrancar (`auto-index-creation`):
  - `ux_usuarios_correo`: único, con colación `strength: 2`, así que `ANA@x` y `ana@x` cuentan como el mismo correo
    (antes era `LOWER(correo)`);
  - `uk_equipos_codigo`: único;
  - `ux_prestamos_equipo_en_curso`: **único parcial**, solo sobre los préstamos APROBADOS o VENCIDOS, igual que en V1;
  - `ix_prestamos_usuario_estado` e `ix_prestamos_estado_fecha`, para las consultas.
- `DatosIniciales` siembra al arrancar el administrador y los 9 equipos de V2, solo si faltan.

### Concurrencia sin triggers

`OperacionesAtomicas` hace con operaciones atómicas de MongoDB lo que hacían los triggers:

| Antes (PostgreSQL) | Ahora (MongoDB) |
|---|---|
| `tg_prestamos_transicion`: solo admite las transiciones de D5 | `guardarSiEstado`: reemplaza el documento solo si en la base de datos sigue en el estado de origen. Si otra operación lo cambió, no escribe nada y el servicio responde 409. |
| Al aprobar, el equipo pasa a PRESTADO en la misma transacción | `guardarSiDisponible`: aprobar toma primero el equipo (solo si sigue DISPONIBLE y activo) y después el préstamo. Si el préstamo ya cambió, devuelve el equipo a DISPONIBLE. |
| `ux_prestamos_equipo_en_curso` (índice único parcial) | El mismo índice, declarado en `Prestamo`. |
| `tg_prestamos_rn01` con `pg_advisory_xact_lock` | `conCandado`: un documento con `_id` único en `candados` por estudiante. Sus solicitudes simultáneas esperan su turno (hasta 10 s) y cuentan bien. Un candado de más de 30 s se considera abandonado. |

Las ediciones y los cambios de estado de un equipo también se guardan con `guardarSiEstado`, para que no pisen un
préstamo aprobado al mismo tiempo.

### Sin Swagger

- Se quitan la dependencia `springdoc-openapi`, las anotaciones `@Tag`, `@Operation`, `@ApiResponse` y `@Schema`,
  las rutas públicas de `SecurityConfig` y los reenvíos de nginx y de Vite.
- El resumen de cada `@Operation` pasa a ser el Javadoc del método del controlador.
- La API se documenta en el README (endpoints y códigos de error) y en la colección de Postman, que es su
  documentación ejecutable.
- `/v3/api-docs` y `/swagger-ui.html` responden 401 sin token y 404 con token. `ApiIntegracionTest` y las peticiones
  00.01 y 00.02 de Postman lo comprueban.

### Diccionario de clases

Se agregan tres clases a la lista blanca de `ArquitecturaTest`: `repository.GeneradorIds`,
`repository.OperacionesAtomicas` y `service.DatosIniciales`. Las 15 dependencias de D7 no cambian. `domain` puede
usar las anotaciones de mapeo de Spring Data MongoDB, igual que antes usaba las de `jakarta.persistence`.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Transacciones de varios documentos | Exigen un *replica set*, que con autenticación necesita un archivo de clave en Docker Compose. Además, un conflicto aborta la transacción en lugar de esperar, así que RN01 con seis solicitudes a la vez necesitaría reintentos. |
| Embeber usuario y equipo dentro del préstamo | Duplica los datos: el estado del equipo quedaría copiado en cada préstamo. |
| Ids `ObjectId` (texto) | Cambia los DTO, las rutas, el frontend, la colección de Postman y el diccionario de D4. |
| Bloqueo optimista (`@Version`) | Agrega un atributo que D4 no tiene y no resuelve RN01, que depende de varios documentos. |
| Una librería de migraciones (Mongock) | Una dependencia más para sembrar diez documentos. |
| Dejar Swagger y apagarlo con una propiedad | El profesor pidió no usarlo; la dependencia seguiría dentro del jar. |

## Consecuencias

- **A favor:** la API no cambia. Los 19 endpoints, los DTO, los ids numéricos y los códigos de error son los
  mismos, así que el frontend y la colección de Postman siguen funcionando (salvo 00.01 y 00.02, que ahora
  comprueban que Swagger ya no existe).
- **A favor:** las reglas siguen protegidas en la base de datos ante peticiones simultáneas, y las mismas pruebas
  de concurrencia de `ApiIntegracionTest` (seis solicitudes a la vez, aprobar contra rechazar) lo verifican.
- **En contra:** sin transacciones, aprobar escribe dos documentos. Si el proceso muere justo entre las dos
  escrituras, el equipo puede quedar PRESTADO con su préstamo todavía PENDIENTE, y habría que corregirlo a mano en
  la base de datos. Es una ventana de milisegundos que solo se abre si la aplicación se cae en ese instante.
- **En contra:** las restricciones CHECK de V1 (devolución en 7 días como máximo, motivo obligatorio al rechazar,
  valores válidos de los enums) ya no se repiten en la base de datos. Las garantiza solo el dominio, que es por
  donde pasan todas las escrituras.
- **En contra:** sin Swagger no hay una página para probar la API desde el navegador. Se usa Postman o `curl`.
