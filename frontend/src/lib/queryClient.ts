import { QueryClient } from '@tanstack/react-query';

import { ApiRequestError } from '@/api/apiClient';

const MAX_NETWORK_RETRIES = 2;

/** Solo se reintenta ante errores de red; un 4xx o 5xx con `ApiError` no cambia al repetirlo. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  return (
    error instanceof ApiRequestError && error.status === 0 && failureCount < MAX_NETWORK_RETRIES
  );
}

/**
 * Opciones de las mutaciones que llevan una contraseña (inicio de sesión, registrar y editar
 * personal): con `gcTime: 0`, la mutación sale de la caché del `QueryClient` en cuanto ningún
 * componente la usa, así la contraseña no queda en el estado global (plan de fases del frontend,
 * Fase 3). Por defecto quedaría ahí 5 minutos.
 */
export const PASSWORD_MUTATION = { gcTime: 0 } as const;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}
