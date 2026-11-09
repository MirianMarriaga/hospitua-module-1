import { type QueryKey, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getLatestDamageReport, reportDamage } from '@/api/damageReportService';

/** Reportar daño; al terminar, bien o mal, se vuelve a consultar el panel que lo abrió. */
export function useReportDamage(refreshKey: QueryKey) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roomId, description }: { roomId: string; description: string }) =>
      reportDamage(roomId, description),
    onSettled: () => queryClient.invalidateQueries({ queryKey: refreshKey }),
  });
}

/** "Ver informe" del daño; solo consulta mientras el diálogo está abierto. */
export function useLatestDamageReport(roomId: string | undefined) {
  return useQuery({
    queryKey: ['damage-report', roomId],
    queryFn: () => getLatestDamageReport(roomId!),
    enabled: roomId !== undefined,
    staleTime: 0,
  });
}
