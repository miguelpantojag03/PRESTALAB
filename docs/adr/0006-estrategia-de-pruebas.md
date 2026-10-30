# ADR 0006: Estrategia de pruebas en cuatro niveles

**Estado:** Aceptada

## Contexto

Cada RF y cada RN deben tener una prueba que los respalde, y la fidelidad a los diagramas debe comprobarse
de forma automática. Unas pruebas que solo levantan toda la aplicación son lentas y, cuando fallan, no dicen
qué capa falló. Unas pruebas que solo usan mocks no detectan errores de SQL, de seguridad ni de serialización.

## Decisión

Se prueba en cuatro niveles, de más rápido a más realista:

| Nivel | Herramienta | Qué verifica | Ejemplos |
|---|---|---|---|
| 1. Dominio y servicios | JUnit 5 y Mockito | Reglas RN, transiciones de D5 y el orden de D6 (`InOrder`) | `PrestamoTest`, `PrestamoServiceImplTest` |
| 2. Arquitectura | ArchUnit y reflexión | Las 15 dependencias de D7 y el diccionario de clases, métodos y endpoints | `ArquitecturaTest`, `FidelidadDiccionarioTest` |
| 3. Capa web | `@WebMvcTest` con la `SecurityConfig` real | Matriz de seguridad (sin token, token alterado, vencido o de otro emisor, rol equivocado), códigos de error y cabecera `Location` | `ControladoresRestTest` |
| 4. Integración | Testcontainers (PostgreSQL 16) | Flyway, consultas JPA, índices únicos, triggers de V3, el flujo D6 completo por HTTP y peticiones simultáneas con hilos (RN01 y aprobar contra rechazar) | `RepositoriosIntegracionTest`, `ApiIntegracionTest` |

Además, fuera de JUnit:

- **End-to-end:** la colección de Postman (77 peticiones) corre con Newman contra los tres contenedores de D8,
  en el job `e2e-postman` del CI. Verifica también los correos en Mailpit (RF08).
- **Cobertura:** JaCoCo hace fallar `mvn verify` si la cobertura baja del umbral configurado en `pom.xml`.

RF09, RF11 y RN04 dependen del paso del tiempo o de tareas programadas. Se prueban en JUnit llamando
directamente al método del servicio, sin agregar endpoints de prueba (ver `postman/README.md`).

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| H2 en memoria para la integración | No soporta igual los índices parciales ni algunos tipos de PostgreSQL. Las pruebas pasarían y producción fallaría. |
| Solo `@SpringBootTest` | Lento y, cuando falla, no se sabe qué capa falló. |
| Endpoints de prueba para forzar el vencimiento | Agregan endpoints fuera del diccionario y abren una puerta en producción. |

## Consecuencias

- **A favor:** un fallo indica la capa. Los niveles 1 a 3 corren en segundos y sin Docker.
- **A favor:** la fidelidad a D5, D6 y D7 está escrita como pruebas, no solo como documentación.
- **En contra:** los niveles 4 y end-to-end necesitan Docker. Sin él, las pruebas de Testcontainers se omiten
  en local (`disabledWithoutDocker`), pero en CI siempre corren.
