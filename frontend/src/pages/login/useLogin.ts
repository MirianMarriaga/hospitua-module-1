import { useMutation } from '@tanstack/react-query';

import { login } from '@/api/authService';
import { useSessionStore } from '@/stores/sessionStore';

/** Inicia sesión y guarda la sesión que devuelve el backend (regla 14 del plan base). */
export function useLogin() {
  const saveSession = useSessionStore((state) => state.login);
  return useMutation({ mutationFn: login, onSuccess: saveSession });
}
