import { useQuery } from '@tanstack/react-query';

import { getRoom } from '@/api/roomService';
import { roomKey } from '@/lib/queryKeys';

/** Una habitación del inventario, para editarla o darla de baja; espera a tener el `roomId`. */
export function useRoom(roomId: string | undefined) {
  return useQuery({
    queryKey: roomKey(roomId),
    queryFn: () => getRoom(roomId ?? ''),
    enabled: !!roomId,
  });
}
