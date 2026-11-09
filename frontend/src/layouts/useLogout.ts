import { useQueryClient } from '@tanstack/react-query';

import { useSessionStore } from '@/stores/sessionStore';

/** Cierra la sesión y descarta los datos del backend en caché, que son de ese usuario. */
export function useLogout() {
  const logout = useSessionStore((state) => state.logout);
  const queryClient = useQueryClient();
  return () => {
    queryClient.clear();
    logout();
  };
}
