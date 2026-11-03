import { QueryClient } from '@tanstack/react-query';

import { ApiRequestError } from '@/api/apiClient';

const MAX_NETWORK_RETRIES = 2;

/** Solo se reintenta ante errores de red; un 4xx o 5xx con `ApiError` no cambia al repetirlo. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  return (
    error instanceof ApiRequestError && error.status === 0 && failureCount < MAX_NETWORK_RETRIES
  );
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}
