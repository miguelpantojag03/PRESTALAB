// Movimiento de la interfaz, según "Designing Fluid Interfaces" (WWDC 2018).
// Todo lo que se puede tocar se anima con resortes: arrancan desde el valor que está en pantalla, conservan la
// velocidad y se pueden interrumpir y revertir en cualquier momento, cosa que una transición CSS no permite.
// En Motion, "duration" de un resorte equivale al "response" de Apple y "bounce" al inverso del amortiguamiento.

/** Por defecto: críticamente amortiguado (damping 1.0, response 0.4). Se asienta sin rebotar. */
export const RESORTE = { type: 'spring', bounce: 0, duration: 0.4 };

/** Para lo que es pequeño y rápido, como un indicador que se desliza entre pestañas. */
export const RESORTE_RAPIDO = { type: 'spring', bounce: 0, duration: 0.3 };

/**
 * Hoja que se suelta con impulso (damping ~0.8, response 0.3). El rebote solo se justifica porque el gesto
 * traía impulso; en un menú que solo aparece, un rebote se sentiría fuera de lugar.
 */
export const RESORTE_HOJA = { type: 'spring', bounce: 0.2, duration: 0.3 };

/**
 * Proyección de impulso de Apple: dónde terminaría el gesto si siguiera desacelerando como un scroll.
 * Se decide con el punto proyectado, no con el punto donde se soltó el dedo.
 */
export function proyectar(velocidadPxPorSegundo, desaceleracion = 0.998) {
  return ((velocidadPxPorSegundo / 1000) * desaceleracion) / (1 - desaceleracion);
}
