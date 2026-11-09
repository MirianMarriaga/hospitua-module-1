import { type QueryKey, useQuery } from '@tanstack/react-query';

/**
 * Actualización de los paneles (recepción T029; limpieza y mantenimiento FR-010): se vuelven a
 * consultar cada 60 segundos y cada vez que el panel se vuelve a mostrar, por ejemplo al volver de
 * una acción (aunque se haya cancelado). No hay notificaciones en tiempo real.
 */
export const PANEL_REFRESH = {
  refetchInterval: 60_000,
  refetchOnMount: 'always',
} as const;

/** Consulta de un panel con su actualización; `select` adapta la respuesta a lo que se muestra. */
export function usePanelQuery<TData, TView = TData>(
  queryKey: QueryKey,
  queryFn: () => Promise<TData>,
  select?: (data: TData) => TView,
) {
  return useQuery({ queryKey, queryFn, select, ...PANEL_REFRESH });
}
