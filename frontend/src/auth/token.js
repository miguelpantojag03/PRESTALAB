// Lectura del JWT en el navegador. Solo se lee el payload para mostrar el nombre y el rol y saber cuándo vence;
// quien valida la firma es el backend (ADR 0001), así que un token alterado igual recibe 401.

export function leerPayload(token) {
  try {
    const parte = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const relleno = parte + '='.repeat((4 - (parte.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(relleno), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

/** Convierte un token en la sesión que usa la interfaz, o null si no sirve o ya venció. */
export function sesionDesdeToken(token, ahoraEnSegundos = Date.now() / 1000) {
  const payload = token ? leerPayload(token) : null;
  if (!payload || typeof payload.exp !== 'number' || payload.exp <= ahoraEnSegundos) {
    return null;
  }
  return {
    token,
    usuarioId: Number(payload.sub),
    nombre: payload.nombre,
    rol: payload.rol,
    expira: payload.exp,
  };
}
