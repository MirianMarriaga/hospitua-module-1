import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  deactivateStaff,
  editStaff,
  getStaffAccount,
  listStaff,
  registerStaff,
  type EditStaffRequest,
  type StaffFilters,
} from '@/api/staffService';
import { PASSWORD_MUTATION } from '@/lib/queryClient';
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
    ...PASSWORD_MUTATION,
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

/**
 * Guarda la edición; al tener éxito, la lista y la cuenta se vuelven a consultar. Ante una edición
 * simultánea (`CONCURRENT_UPDATE`), la página recarga la cuenta cuando el Administrador lo pide.
 */
export function useEditStaff(username: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: EditStaffRequest) => editStaff(username, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEY });
      void queryClient.invalidateQueries({ queryKey: staffAccountKey(username) });
    },
    ...PASSWORD_MUTATION,
  });
}

/**
 * Desactiva la cuenta. Al terminar, bien o mal, la lista y la cuenta se vuelven a consultar: un
 * rechazo como `ACCOUNT_INACTIVE` dice que otro Administrador ya la cambió.
 */
export function useDeactivateStaff(username: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => deactivateStaff(username),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: STAFF_KEY });
      void queryClient.invalidateQueries({ queryKey: staffAccountKey(username) });
    },
  });
}
