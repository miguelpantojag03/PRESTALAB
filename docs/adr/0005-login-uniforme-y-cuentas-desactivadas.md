# ADR 0005: Login uniforme y cuentas desactivadas con token vigente

**Estado:** Aceptada

## Contexto

Hay dos problemas de seguridad relacionados:

1. **Enumeración de usuarios.** Si el login responde "correo no registrado" para un correo y "contraseña
   incorrecta" para otro, un atacante descubre qué correos existen. Lo mismo pasa si un caso tarda más que el
   otro, porque BCrypt con costo 12 tarda unos cientos de milisegundos y un correo inexistente respondería al
   instante.
2. **Tokens que no se pueden revocar.** Por el ADR 0001, un JWT sigue siendo válido hasta que vence. Si el
   administrador desactiva a un estudiante (RF12), ese estudiante conserva un token vigente hasta por 60 minutos.

## Decisión

- **Login uniforme:** correo inexistente, contraseña incorrecta y cuenta inactiva lanzan la misma
  `BadCredentialsException`. La respuesta es un 401 `CREDENCIALES_INVALIDAS` con el mensaje "Correo o
  contraseña incorrectos".
- **Mismo costo de tiempo:** si el correo no existe, `UsuarioServiceImpl` compara igual la contraseña contra
  un hash BCrypt fijo (`HASH_SENUELO`). Así ambos caminos hacen el mismo trabajo.
- **Cuenta desactivada con token vigente (decisión 3.12.1):**
  - Una desactivada no puede iniciar sesión.
  - Si usa un token emitido antes de la desactivación, `solicitarPrestamo` relee al usuario desde la base de
    datos y responde 422 `REGLA_NEGOCIO` ("Tu cuenta está desactivada").
- **Cuentas de administrador:** `cambiarActivo` rechaza con 422 cualquier cambio sobre una cuenta ADMIN,
  incluida la propia. Así el sistema nunca se queda sin administrador.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Mensajes distintos por caso | Permite enumerar usuarios. |
| Lista negra de tokens revocados | Vuelve a meter estado en el servidor (contradice el ADR 0001) y no está en el diccionario. |
| Revisar `activo` en un filtro, en cada petición | Agrega una consulta a la base de datos por cada petición. La operación que importa es crear préstamos, y esa ya consulta al usuario. |
| Tokens de 5 minutos | Reducen la ventana, pero obligan a iniciar sesión a cada rato sin refresh tokens. |

## Consecuencias

- **A favor:** el login no revela qué correos están registrados, ni por el mensaje ni por el tiempo.
- **A favor:** un estudiante desactivado no puede crear préstamos nuevos, aunque su token siga vigente.
- **En contra:** durante esos 60 minutos todavía puede **consultar** el catálogo y su historial. Se acepta
  porque son datos de solo lectura y propios. La carpeta 06 de Postman y `ApiIntegracionTest` lo prueban.
