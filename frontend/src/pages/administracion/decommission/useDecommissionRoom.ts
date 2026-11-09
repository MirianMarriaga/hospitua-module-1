import { useMutation, useQueryClient } from '@tanstack/react-query';

import { decommissionRoom, type DecommissionRoomRequest } from '@/api/roomService';
import { INVENTORY_KEY, roomKey } from '@/lib/queryKeys';

interface DecommissionVariables {
  roomId: string;
  request: DecommissionRoomRequest;
}

/** Da de baja la habitación; el inventario y la habitación se vuelven a consultar. */
export function useDecommissionRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roomId, request }: DecommissionVariables) => decommissionRoom(roomId, request),
    onSuccess: (_result, { roomId }) => {
      void queryClient.invalidateQueries({ queryKey: INVENTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: roomKey(roomId) });
    },
  });
}
