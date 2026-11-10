import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { listStaff, registerStaff, type StaffFilters } from '@/api/staffService';
import { STAFF_KEY } from '@/lib/queryKeys';

/** Página de la lista del personal; mientras llega la siguiente se sigue viendo la anterior. */
export function useStaffList(filters: StaffFilters, page: number) {
  return useQuery({
    queryKey: [...STAFF_KEY, filters, page],
    queryFn: () => listStaff(filters, page),
    placeholderData: keepPreviousData,
  });
}

/** Registra una cuenta; la lista del personal se vuelve a consultar. */
export function useRegisterStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: registerStaff,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: STAFF_KEY }),
  });
}
