// Fechas y textos de la interfaz. Las fechas usan America/Bogota, igual que el backend, para que "hoy" y el
// límite de 7 días de RN02 coincidan aunque el navegador esté en otra zona horaria.

const ZONA = 'America/Bogota';

/** Fecha de hoy en Bogotá como "AAAA-MM-DD" (el formato de la API y de <input type="date">). */
export function hoyEnBogota(ahora = new Date()) {
  return ahora.toLocaleDateString('en-CA', { timeZone: ZONA });
}

export function sumarDias(fechaIso, dias) {
  const [anio, mes, dia] = fechaIso.split('-').map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia + dias));
  return fecha.toISOString().slice(0, 10);
}

export function formatearFecha(fechaIso) {
  if (!fechaIso) {
    return '—';
  }
  const [anio, mes, dia] = fechaIso.slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}

export function formatearFechaHora(fechaHoraIso) {
  if (!fechaHoraIso) {
    return '—';
  }
  return `${formatearFecha(fechaHoraIso)} ${fechaHoraIso.slice(11, 16)}`;
}

export const CATEGORIAS = {
  PORTATIL: 'Portátil',
  CAMARA: 'Cámara',
  PROYECTOR: 'Proyector',
  KIT_ELECTRONICO: 'Kit electrónico',
  OTRO: 'Otro',
};

export const ESTADOS_EQUIPO = {
  DISPONIBLE: 'Disponible',
  PRESTADO: 'Prestado',
  EN_MANTENIMIENTO: 'En mantenimiento',
};

export const ESTADOS_PRESTAMO = {
  PENDIENTE: 'Pendiente',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  DEVUELTO: 'Devuelto',
  VENCIDO: 'Vencido',
};
