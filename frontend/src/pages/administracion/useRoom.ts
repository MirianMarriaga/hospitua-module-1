import { useQuery } from '@tanstack/react-query';

import { getRoom } from '@/api/roomService';
import { roomKey } from '@/lib/queryKeys';

/**
 * Una habitación del inventario, para editarla, darla de baja o explicar por qué no se puede
 * reactivar; espera a tener el `roomId` y a que `enabled` (por defecto `true`) lo permita.
 */
export function useRoom(roomId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: roomKey(roomId),
    queryFn: () => getRoom(roomId ?? ''),
    enabled: !!roomId && enabled,
  });
}
