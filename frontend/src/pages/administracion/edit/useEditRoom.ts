import { useMutation, useQueryClient } from '@tanstack/react-query';

import { editRoom, type EditRoomRequest } from '@/api/roomService';
import { INVENTORY_KEY, roomKey } from '@/lib/queryKeys';

/** Guarda la edición; el inventario y la habitación se vuelven a consultar. */
export function useEditRoom(roomId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: EditRoomRequest) => editRoom(roomId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: INVENTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: roomKey(roomId) });
    },
  });
}
