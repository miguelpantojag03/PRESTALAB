# ADR 0007: Frontend React en un contenedor propio

**Estado:** Aceptada. Amplía D1 y D8.

## Contexto

La v3 dejó PrestaLab como una API REST pura (ADR 0001). D1 decía "alcance sin frontend" y la API solo se
podía usar desde Swagger UI o Postman. Eso sirve para probar, pero no para que un estudiante o el
administrador del laboratorio usen el sistema en el día a día.

Había que agregar una interfaz sin deshacer lo que ya estaba verificado: el diccionario de clases, las 15
dependencias de D7, los 19 endpoints y las pruebas del backend.

## Decisión

- El frontend es una **SPA en React 18 con Vite**, en la carpeta `frontend/` del mismo repositorio. Es un
  proyecto aparte: no hay JavaScript dentro de la aplicación Java ni archivos en `src/main/resources/static`.
- Solo consume la API pública `/api/v1`. No hay endpoints nuevos ni cambios en el backend.
- Se despliega como un **cuarto contenedor, `prestalab-web`**: Node 20 compila y `nginx-unprivileged` sirve el
  resultado sin correr como root, igual que `prestalab-app`.
- nginx también **reenvía `/api`, `/swagger-ui` y `/v3/api-docs` a `prestalab-app`**. El navegador ve un solo
  origen (`http://localhost:3000`), así que no hace falta CORS y `SecurityConfig` no cambia. En desarrollo, el
  proxy de Vite hace lo mismo.
- **Sesión:**
  - El JWT se guarda en `sessionStorage`, así que se borra al cerrar la pestaña.
  - El frontend solo lee el payload (nombre, rol, vencimiento) para la navegación.
  - La seguridad la sigue aplicando el backend: un estudiante que fuerce una ruta de administrador recibe 403.
  - Un 401 `NO_AUTENTICADO` cierra la sesión y lleva al inicio de sesión.
- **Errores:** el frontend muestra el `detail` del ProblemDetail (ADR 0003) y los errores por campo de
  `errores`. Las reglas viven en un solo lugar, el backend; la interfaz solo las explica.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Volver a las vistas Thymeleaf de la v2 | Deshace la v3, mezcla presentación con API y rompe ArchUnit (`@Controller` con vistas). |
| Servir el frontend desde Spring Boot (`static/`) | Mete un build de Node en el de Maven y JavaScript en el jar de la API. |
| Habilitar CORS y servir el frontend desde otro origen | Cambia `SecurityConfig` y amplía la superficie de ataque. Con el proxy no hace falta. |
| Angular | Más estructura de la necesaria para ocho pantallas, y más lento de armar. |
| Guardar el token en `localStorage` | Sobrevive al cierre del navegador. `sessionStorage` limita el tiempo de exposición en equipos compartidos del laboratorio. |

## Consecuencias

- **A favor:** el backend no cambia, así que sus pruebas, ArchUnit, el diccionario y la colección de Postman
  siguen valiendo tal cual.
- **A favor:** frontend y backend se despliegan y se escalan por separado, y el frontend se puede reemplazar sin
  tocar la API.
- **A favor:** el CI agrega un job `frontend` (Vitest y build) y comprueba que el contenedor sirve la aplicación
  y reenvía la API.
- **En contra:** **D1 y D8 cambian.** D1 ya no es "sin frontend" y D8 pasa de 3 a 4 contenedores. Hay que
  actualizar esos diagramas en el documento de diseño.
- **En contra:** el administrador solo ve los equipos activos, porque la API (`GET /equipos`) no expone los
  desactivados. Mostrarlos exigiría un endpoint que el diccionario no tiene.
