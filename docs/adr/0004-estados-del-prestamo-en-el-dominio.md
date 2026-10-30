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
- Ante dos aprobaciones concurrentes del mismo equipo, la base de datos tiene la última palabra: el índice
  único parcial `ux_prestamos_equipo_en_curso` y `saveAndFlush` hacen fallar la segunda aprobación dentro de
  la transacción, con 422.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Validar las transiciones en el servicio | La regla quedaría lejos del dato y otro servicio podría saltársela (modelo anémico). |
| Patrón State (una clase por estado) | Cinco clases para cuatro métodos de transición. El diccionario no las incluye y un `switch` sobre el enum es más claro. |
| Spring Statemachine | Una dependencia pesada para una máquina de estados que cabe en una entidad. |
| Bloqueo optimista (`@Version`) | Detecta que alguien editó el mismo préstamo, no dos préstamos distintos sobre el mismo equipo. El índice sí lo detecta. |

## Consecuencias

- **A favor:** es imposible dejar un préstamo en un estado que D5 no permite. `PrestamoTest` prueba cada
  transición válida y cada una inválida sin levantar Spring.
- **A favor:** el 409 del API sale directamente de la entidad, sin lógica repetida.
- **En contra:** la entidad JPA tiene comportamiento, así que hay que tener cuidado al cargarla. Los métodos
  no dependen de relaciones perezosas sin inicializar.
