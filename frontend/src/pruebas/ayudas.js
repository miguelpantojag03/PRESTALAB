const base64Url = (objeto) =>
  btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(objeto))))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

/** JWT con el payload dado. La firma es falsa: el frontend no la valida, eso lo hace el backend. */
export const tokenDePrueba = (payload) => `${base64Url({ alg: 'HS256' })}.${base64Url(payload)}.firma`;

/** Opciones de React Router v7 activadas desde ya, en la app y en las pruebas. */
export const FUTURO_ROUTER = { v7_startTransition: true, v7_relativeSplatPath: true };
