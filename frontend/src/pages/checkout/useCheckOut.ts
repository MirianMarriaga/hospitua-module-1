import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getActiveStay, registerCheckOut, type CheckOutRequest } from '@/api/checkOutService';
import { RECEPTION_KEY, settlementKey } from '@/lib/queryKeys';

/** Paso 1: la estadía activa de la habitación; siempre se pide de nuevo al abrir el paso. */
export function useActiveStay(reservationRef: string, roomId: string) {
  return useQuery({
    queryKey: ['check-out', 'active-stay', reservationRef, roomId],
    queryFn: () => getActiveStay(reservationRef, roomId),
    staleTime: 0,
  });
}

/**
 * Paso 4: registra la salida. El panel de recepción se vuelve a consultar y la liquidación de la
 * estadía se descarta, porque ya no es la de una estadía activa.
 */
export function useRegisterCheckOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CheckOutRequest) => registerCheckOut(request),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: RECEPTION_KEY });
      queryClient.removeQueries({ queryKey: settlementKey(result.stayId) });
    },
  });
}
