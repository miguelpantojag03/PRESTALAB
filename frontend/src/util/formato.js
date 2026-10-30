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

/** Días de calendario de "desde" a "hasta" (fechas AAAA-MM-DD); negativo si "hasta" ya pasó. */
export function diasEntre(desde, hasta) {
  const utc = (iso) => {
    const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
    return Date.UTC(a, m - 1, d);
  };
  return Math.round((utc(hasta) - utc(desde)) / 864e5);
}

const DIA_CORTO = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });

/** "lun 2 nov": para fechas cercanas, donde importa el día de la semana. */
export function fechaCorta(fechaIso) {
  const [a, m, d] = fechaIso.slice(0, 10).split('-').map(Number);
  return DIA_CORTO.format(new Date(Date.UTC(a, m - 1, d))).replace(/\./g, '');
}

/** Cuándo vence una devolución, en palabras y contada desde hoy en Bogotá: "Hoy", "Mañana", "En 3 días", "Hace 2 días". */
export function cuandoDevolver(fechaIso, hoy = hoyEnBogota()) {
  const dias = diasEntre(hoy, fechaIso);
  if (dias === 0) return { texto: 'Hoy', dias };
  if (dias === 1) return { texto: 'Mañana', dias };
  if (dias > 1) return { texto: `En ${dias} días`, dias };
  if (dias === -1) return { texto: 'Ayer', dias };
  return { texto: `Hace ${-dias} días`, dias };
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

/** Plurales para los conteos de las tarjetas y del estante. */
export const CATEGORIAS_PLURAL = {
  PORTATIL: 'Portátiles',
  CAMARA: 'Cámaras',
  PROYECTOR: 'Proyectores',
  KIT_ELECTRONICO: 'Kits electrónicos',
  OTRO: 'Otros',
};

export const ESTADOS_PRESTAMO = {
  PENDIENTE: 'Pendiente',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
  DEVUELTO: 'Devuelto',
  VENCIDO: 'Vencido',
};
