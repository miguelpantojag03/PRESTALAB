# ADR 0001: API REST stateless autenticada con JWT HS256

**Estado:** Aceptada

## Contexto

La v2 era una aplicación web con vistas Thymeleaf, sesión HTTP y formulario de login. Los diagramas v3 (D1 y D7)
piden una API REST pura, sin frontend, que pueda consumir cualquier cliente (Postman, Swagger UI, una app
móvil o un frontend futuro). Con una sesión en el servidor, cada cliente tendría que manejar cookies y CSRF, y
escalar a más de una instancia exigiría compartir las sesiones.

## Decisión

- La API es **stateless**: no hay `HttpSession`, ni formulario de login, ni CSRF.
- `POST /api/v1/auth/login` devuelve un **JWT firmado con HS256** que el cliente envía en
  `Authorization: Bearer <token>`.
- El token lleva los claims `iss=prestalab`, `sub` (id del usuario), `rol`, `nombre`, `iat` y `exp`, y vence
  a los 60 minutos.
- Se usa el soporte que trae Spring Security (`spring-boot-starter-oauth2-resource-server`, con Nimbus).
  `SecurityConfig` define el `JwtEncoder`, el `JwtDecoder` (que valida firma, expiración y emisor) y el
  convertidor que pasa el claim `rol` a `ROLE_ADMIN` o `ROLE_ESTUDIANTE`.
- El secreto llega por la variable de entorno `PRESTALAB_JWT_SECRET`. La aplicación no arranca si tiene menos
  de 32 bytes, que es lo mínimo para HS256.
- Los controladores toman al usuario del `sub` del token y nunca del cuerpo ni de la URL. Así un estudiante no
  puede pedir un préstamo a nombre de otro.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Mantener la sesión HTTP | Exige cookies y CSRF y no escala sin compartir sesiones. Contradice D1. |
| Librería `jjwt` | Agrega una dependencia que Spring Security ya resuelve. El prompt la prohíbe. |
| RS256 (par de claves) | Sirve cuando otro servicio debe verificar tokens sin poder firmarlos. Aquí firma y verifica el mismo servicio. |
| Refresh tokens | No están en el diccionario de clases. Con 60 minutos, volver a iniciar sesión es aceptable para el alcance. |

## Consecuencias

- **A favor:** cualquier cliente HTTP usa la API, cada petición se autentica sola y no hay estado que
  compartir entre instancias.
- **A favor:** la autorización se aplica en dos capas: reglas por URL en `SecurityConfig` y `@PreAuthorize`
  en cada endpoint.
- **En contra:** un token emitido no se puede revocar antes de que venza. El ADR 0005 explica cómo se maneja
  una cuenta desactivada mientras su token sigue vigente.
- **En contra:** si el secreto se filtra, cualquiera puede emitir tokens. Por eso no está en el repositorio
  y en CI se genera uno nuevo en cada ejecución.
