import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Ejecuta una consulta a la API al montar la página y la deja disponible para recargar tras cada acción.
 * Si hay varias recargas en vuelo (una acción y su Deshacer, o dos clics en Actualizar), solo la última
 * actualiza la pantalla: una respuesta vieja que llega tarde nunca pisa datos más nuevos.
 */
export function useCarga(consulta) {
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(true);
  const ultima = useRef(0);

  const recargar = useCallback(async () => {
    const numero = ++ultima.current;
    setCargando(true);
    try {
      const resultado = await consulta();
      if (numero === ultima.current) {
        setDatos(resultado);
        setError(null);
      }
    } catch (e) {
      if (numero === ultima.current) {
        setError(e);
      }
    } finally {
      if (numero === ultima.current) {
        setCargando(false);
      }
    }
  }, [consulta]);

  useEffect(() => {
    recargar();
    // Al desmontar, ninguna respuesta pendiente vuelve a tocar el estado.
    return () => {
      ultima.current += 1;
    };
  }, [recargar]);

  return { datos, error, cargando, recargar };
}
