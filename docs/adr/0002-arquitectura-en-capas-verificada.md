# ADR 0002: Arquitectura en capas verificada con ArchUnit

**Estado:** Aceptada

## Contexto

D7 fija los paquetes de PrestaLab y las 15 dependencias permitidas entre ellos. Una regla escrita solo en
un diagrama se rompe sin que nadie lo note: basta con que un controlador importe un repositorio "por esta vez".

## Decisión

- El código se organiza **por capas técnicas**, un paquete por cada caja de D7: `controller`, `service`,
  `repository`, `domain`, `dto`, `mapper`, `notification`, `scheduler`, `web.error` y `config`.
- Los controladores solo conocen las **interfaces** de servicio (`EquipoService`, `PrestamoService`, etc.),
  nunca sus implementaciones ni los repositorios (D2).
- Las entidades no salen de la capa de servicio: los controladores reciben y devuelven DTO, y los mappers
  hacen la conversión a mano.
- `config` no depende de ningún paquete de la aplicación. Solo arma la infraestructura (seguridad, JWT,
  `PasswordEncoder`).
- `ArquitecturaTest` (ArchUnit) convierte D7 en pruebas. Verifica que no exista ninguna dependencia fuera de
  las 15, que todo controlador sea `@RestController` y que no haya `@Controller` con vistas.
- `FidelidadDiccionarioTest` verifica que las clases, los métodos y los 19 endpoints sean exactamente los del
  diccionario.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Paquetes por funcionalidad (`prestamo/`, `equipo/`) | No corresponde a D7, que está dibujado por capas. |
| Arquitectura hexagonal (puertos y adaptadores) | Agrega interfaces y adaptadores que el diccionario no tiene. Para tres entidades sería sobreingeniería. |
| Lombok o MapStruct | Generan código oculto, más difícil de explicar en la sustentación. El prompt los prohíbe. |
| Revisar las dependencias a mano | No escala y se olvida. ArchUnit las revisa en cada `mvn verify` y en CI. |

## Consecuencias

- **A favor:** si alguien rompe D7, el build falla y dice qué clase depende de cuál.
- **A favor:** como los controladores dependen de interfaces, se prueban con servicios simulados
  (`ControladoresRestTest`) sin levantar la base de datos.
- **En contra:** hay más archivos (interfaz más implementación, DTO más mapper) que en un CRUD directo.
- **En contra:** agregar una clase o un endpoint exige actualizar a propósito el diccionario y sus pruebas.
  Es intencional: así el código no se aleja de los diagramas.
