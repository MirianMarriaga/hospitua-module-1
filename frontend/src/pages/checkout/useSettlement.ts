import { useQuery } from '@tanstack/react-query';

import { getSettlement } from '@/api/settlementService';
import { settlementKey } from '@/lib/queryKeys';

/**
 * Una sola consulta para los pasos 2 y 3. La respuesta es `no-store`: siempre se pide de nuevo
 * (`staleTime` 0) y sin reintentos, porque una falla del servicio ya llega como `UNAVAILABLE`.
 */
export function useSettlement(stayId: string) {
  return useQuery({
    queryKey: settlementKey(stayId),
    queryFn: () => getSettlement(stayId),
    staleTime: 0,
    retry: false,
  });
}
