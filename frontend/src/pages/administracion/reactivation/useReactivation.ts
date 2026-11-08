import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getRoomDecommission, reactivateRoom, type ReactivateRoomRequest } from '@/api/roomService';
import { INVENTORY_KEY, roomKey } from '@/lib/queryKeys';

function decommissionKey(roomId: string | undefined) {
  return ['decommission', roomId] as const;
}

/** La baja vigente de la habitación, que la confirmación muestra; espera a tener el `roomId`. */
export function useRoomDecommission(roomId: string | undefined) {
  return useQuery({
    queryKey: decommissionKey(roomId),
    queryFn: () => getRoomDecommission(roomId ?? ''),
    enabled: !!roomId,
  });
}

interface ReactivateVariables {
  roomId: string;
  request: ReactivateRoomRequest;
}

/** Reactiva la habitación; el inventario, la habitación y su baja se vuelven a consultar. */
export function useReactivateRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roomId, request }: ReactivateVariables) => reactivateRoom(roomId, request),
    onSuccess: (_result, { roomId }) => {
      void queryClient.invalidateQueries({ queryKey: INVENTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: roomKey(roomId) });
      void queryClient.invalidateQueries({ queryKey: decommissionKey(roomId) });
    },
  });
}
