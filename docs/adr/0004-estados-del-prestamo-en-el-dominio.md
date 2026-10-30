# ADR 0004: Las transiciones del préstamo viven en la entidad

**Estado:** Aceptada

## Contexto

D5 define cinco estados del préstamo (PENDIENTE, APROBADO, RECHAZADO, DEVUELTO y VENCIDO) y sus
transiciones. Si cada servicio hiciera `prestamo.setEstado(...)`, nada impediría pasar de RECHAZADO a APROBADO,
y la regla quedaría repartida en varios lugares.

## Decisión

- `Prestamo` no expone `setEstado`. Cada transición es un método de la entidad (`aprobar`, `rechazar`,
  `devolver`, `marcarVencido`) que verifica el estado de origen. Si la transición no está en D5,
  lanza `TransicionInvalidaException`, que el API responde con 409.
- Las guardas de D5 también viven en la entidad: `aprobar()` exige `[equipo DISPONIBLE]` y `rechazar(motivo)`
  exige un motivo no vacío. Al aprobar, el equipo pasa a PRESTADO, y al devolver vuelve a DISPONIBLE, en la
  misma transacción.
- La guarda `[hoy > fechaDevolucionEsperada]` de `marcarVencido()` la evalúa el servicio con
  `estaVencido(hoy)`, porque depende de la fecha actual.
- Los servicios orquestan en el orden de D6: validan las reglas, cargan las entidades, llaman a la transición,
  guardan y notifican. La prueba `InOrder` de `PrestamoServiceImplTest` fija ese orden.
- Ante peticiones simultáneas, la base de datos tiene la última palabra. La verificación de la entidad no basta:
  dos transacciones pueden leer el mismo préstamo PENDIENTE, una aprobarlo y la otra rechazarlo, y la última
  escritura gana. Se probó así: en 15 de 15 intentos el préstamo quedaba RECHAZADO con el equipo PRESTADO para
  siempre. Por eso la migración V3 agrega el trigger `tg_prestamos_transicion`, que repite en PostgreSQL las
  transiciones de D5. La segunda operación ve el estado que dejó la primera, falla, y el servicio la responde con
  409 antes de notificar.
- La misma migración protege RN01 con `tg_prestamos_rn01`, que toma un candado por usuario y vuelve a contar los
  préstamos activos.
- Ante dos aprobaciones concurrentes del mismo equipo, el índice
  único parcial `ux_prestamos_equipo_en_curso` y `saveAndFlush` hacen fallar la segunda aprobación dentro de
  la transacción, con 422.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Validar las transiciones en el servicio | La regla quedaría lejos del dato y otro servicio podría saltársela (modelo anémico). |
| Patrón State (una clase por estado) | Cinco clases para cuatro métodos de transición. El diccionario no las incluye y un `switch` sobre el enum es más claro. |
| Spring Statemachine | Una dependencia pesada para una máquina de estados que cabe en una entidad. |
| Bloqueo optimista (`@Version`) | Detecta que alguien editó el mismo préstamo, pero agrega un atributo que no está en el diccionario de D4. Tampoco detecta dos préstamos distintos sobre el mismo equipo; el índice sí. |
| `@Lock(PESSIMISTIC_WRITE)` en los repositorios | Agrega métodos que el diccionario no tiene, y bloquear al usuario al inicio cambiaría el orden de llamadas de D6. Los triggers de V3 protegen lo mismo sin tocar el código de D4 a D7. |

## Consecuencias

- **A favor:** es imposible dejar un préstamo en un estado que D5 no permite. `PrestamoTest` prueba cada
  transición válida y cada una inválida sin levantar Spring.
- **A favor:** el 409 del API sale directamente de la entidad, sin lógica repetida.
- **En contra:** la entidad JPA tiene comportamiento, así que hay que tener cuidado al cargarla. Los métodos
  no dependen de relaciones perezosas sin inicializar.
