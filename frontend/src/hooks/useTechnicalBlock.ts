import { type QueryKey, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  getCurrentTechnicalBlock,
  scheduleTechnicalBlock,
  type ScheduleTechnicalBlockRequest,
} from '@/api/technicalBlockService';

/** Programar mantenimiento; al terminar, bien o mal, se vuelve a consultar el panel que lo abrió. */
export function useScheduleTechnicalBlock(refreshKey: QueryKey) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roomId, request }: { roomId: string; request: ScheduleTechnicalBlockRequest }) =>
      scheduleTechnicalBlock(roomId, request),
    onSettled: () => queryClient.invalidateQueries({ queryKey: refreshKey }),
  });
}

/** "Ver informe" del bloqueo; solo consulta mientras el diálogo está abierto. */
export function useCurrentTechnicalBlock(roomId: string | undefined) {
  return useQuery({
    queryKey: ['technical-block', roomId],
    queryFn: () => getCurrentTechnicalBlock(roomId!),
    enabled: roomId !== undefined,
    staleTime: 0,
  });
}
