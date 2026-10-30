# ADR 0003: Errores con ProblemDetail (RFC 9457) y códigos propios

**Estado:** Aceptada

## Contexto

Una API sin un formato de error común obliga a cada cliente a interpretar respuestas distintas: la página
HTML de Tomcat, el JSON de Spring Boot o mensajes sueltos. Además, el cliente tiene que distinguir entre un
dato inválido (400), una transición imposible (409) y una regla de negocio incumplida (422).

## Decisión

- Todo error se responde con `application/problem+json` (RFC 9457) y los campos `type`, `title`, `status`,
  `detail` e `instance`.
- Se agregan dos propiedades: `codigo`, un código estable que el cliente puede comparar, y `timestamp`. Los
  errores de validación agregan además `errores`, un mapa de campo a mensaje.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) traduce las excepciones. Los 401 y 403 que ocurren antes
  de llegar al controlador los escriben el entry point y el `accessDeniedHandler` de `SecurityConfig`, con el
  mismo formato.

| Código | Estado | Cuándo |
|---|---|---|
| `VALIDACION` | 400 | Falla Bean Validation (`@NotBlank`, `@FutureOrPresent`, etc.) |
| `PETICION_INVALIDA` | 400 | JSON mal formado, enum inválido, propiedad desconocida, tipo de parámetro incorrecto |
| `NO_AUTENTICADO` | 401 | No hay token, o es inválido o está vencido |
| `CREDENCIALES_INVALIDAS` | 401 | El login falla (ver ADR 0005) |
| `ACCESO_DENEGADO` | 403 | El rol no permite la operación |
| `RECURSO_NO_ENCONTRADO` | 404 | El id no existe |
| `METODO_NO_PERMITIDO` | 405 | Por ejemplo `DELETE` sobre un equipo (RN07) |
| `TRANSICION_INVALIDA` | 409 | La transición no está en D5 |
| `REGLA_NEGOCIO` | 422 | Se incumple una RN (RN01, RN02, RN04, correo duplicado, etc.) |
| `ERROR_INTERNO` | 500 | Error no previsto; el detalle no expone la traza |

- Jackson usa `fail-on-unknown-properties: true`. Así, un `{"rol": "ADMIN"}` colado en el registro da 400 en
  lugar de ignorarse en silencio.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Formato JSON propio (`{"error": "..."}`) | Es reinventar un estándar que Spring 6 ya implementa con `ProblemDetail`. |
| Responder 400 a todos los errores de negocio | El cliente no distinguiría "dato mal escrito" de "no puedes tener dos préstamos". |
| 409 para toda regla de negocio | 409 es un conflicto con el estado del recurso, que en PrestaLab es la máquina de estados. 422 es una petición bien formada que la regla no admite. |

## Consecuencias

- **A favor:** un solo formato. Los clientes y la colección de Postman validan el mismo esquema en todas las
  respuestas de error.
- **A favor:** `codigo` no depende del idioma del mensaje, así que se puede traducir `detail` sin romper a los
  clientes.
- **En contra:** cada excepción nueva necesita su caso en `GlobalExceptionHandler`. Las que no lo tienen caen
  en 500, y las pruebas lo detectarían.
