import { useEffect, useState } from 'react';

/** Cada cuánto se vuelve a calcular el tiempo transcurrido. */
export const ELAPSED_REFRESH_MS = 15_000;

/**
 * Minutos desde `startDateTime`, como el contador "Tiempo transcurrido" de las vistas de tarea
 * activa de limpieza y mantenimiento; se actualiza solo. Sin fecha, o con una fecha ilegible o
 * futura, devuelve 0.
 */
export function useElapsedMinutes(startDateTime: string | undefined): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), ELAPSED_REFRESH_MS);
    return () => window.clearInterval(id);
  }, []);
  if (!startDateTime) return 0;
  const start = Date.parse(startDateTime);
  return Number.isNaN(start) ? 0 : Math.max(0, Math.floor((now - start) / 60_000));
}
