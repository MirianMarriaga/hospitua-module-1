import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  editStaff,
  getStaffAccount,
  listStaff,
  registerStaff,
  type EditStaffRequest,
  type StaffFilters,
} from '@/api/staffService';
import { STAFF_KEY, staffAccountKey } from '@/lib/queryKeys';

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

/** Una cuenta con su `version`, para editarla o desactivarla; espera a tener el usuario. */
export function useStaffAccount(username: string | undefined) {
  return useQuery({
    queryKey: staffAccountKey(username),
    queryFn: () => getStaffAccount(username ?? ''),
    enabled: !!username,
  });
}

/** Guarda la edición; la lista y la cuenta se vuelven a consultar, también si otro la cambió. */
export function useEditStaff(username: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: EditStaffRequest) => editStaff(username, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEY });
      void queryClient.invalidateQueries({ queryKey: staffAccountKey(username) });
    },
  });
}
