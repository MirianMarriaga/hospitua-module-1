import { useMutation, useQueryClient } from '@tanstack/react-query';

import { registerRoom } from '@/api/roomService';
import { INVENTORY_KEY } from '@/lib/queryKeys';

/** Registra una habitación; el inventario se vuelve a consultar. */
export function useRegisterRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: registerRoom,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: INVENTORY_KEY }),
  });
}
