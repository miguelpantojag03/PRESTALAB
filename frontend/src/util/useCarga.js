import { useCallback, useEffect, useState } from 'react';

/** Ejecuta una consulta a la API al montar la página y la deja disponible para recargar tras cada acción. */
export function useCarga(consulta) {
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(true);

  const recargar = useCallback(async () => {
    setCargando(true);
    try {
      setDatos(await consulta());
      setError(null);
    } catch (e) {
      setError(e);
    } finally {
      setCargando(false);
    }
  }, [consulta]);

  useEffect(() => {
    recargar();
  }, [recargar]);

  return { datos, error, cargando, recargar };
}
