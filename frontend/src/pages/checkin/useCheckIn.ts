import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { registerCheckIn, validateCheckIn, type CheckInRequest } from '@/api/checkInService';
import { RECEPTION_KEY } from '@/lib/queryKeys';

/** Paso 1: valida la reserva y la habitación; siempre se pide de nuevo al abrir el paso. */
export function useCheckInValidation(reservationRef: string, roomId: string) {
  return useQuery({
    queryKey: ['check-in', 'validate', reservationRef, roomId],
    queryFn: () => validateCheckIn(reservationRef, roomId),
    staleTime: 0,
  });
}

/** Paso 3: registra el Check-In; el panel de recepción se vuelve a consultar. */
export function useRegisterCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CheckInRequest) => registerCheckIn(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: RECEPTION_KEY }),
  });
}
