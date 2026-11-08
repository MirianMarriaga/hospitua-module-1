/**
 * Actualización del panel de recepción (FR-006 de `plan-consultar-panel-recepcion.md`, T029): los
 * KPI, las llegadas y las salidas se vuelven a consultar cada 60 segundos y cada vez que el panel
 * se vuelve a mostrar, por ejemplo al volver de un Check-In o de un Check-Out (aunque se haya
 * cancelado). No hay notificaciones en tiempo real.
 */
export const PANEL_REFRESH = {
  refetchInterval: 60_000,
  refetchOnMount: 'always',
} as const;
