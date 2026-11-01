import type { ApiError } from '@/api/types';
import { useSessionStore } from '@/stores/sessionStore';

/** Error de una llamada al backend, con el `ApiError` ya leído. `status` 0 es un error de red. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly errorCode: string;
  readonly details: Record<string, unknown>;

  constructor(
    status: number,
    errorCode: string,
    message: string,
    details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.errorCode = errorCode;
    this.details = details ?? {};
  }
}

export const NETWORK_ERROR = 'NETWORK_ERROR';
export const UNEXPECTED_ERROR = 'UNEXPECTED_ERROR';

type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST';
  params?: Record<string, QueryValue>;
  body?: unknown;
  signal?: AbortSignal;
}

function buildUrl(path: string, params?: Record<string, QueryValue>): string {
  if (!params) {
    return path;
  }
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `${path}?${query}` : path;
}

async function readApiError(response: Response): Promise<ApiRequestError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    if (typeof body.errorCode === 'string') {
      return new ApiRequestError(response.status, body.errorCode, body.message ?? '', body.details);
    }
  } catch {
    // Sin cuerpo ApiError: se trata como error inesperado.
  }
  return new ApiRequestError(response.status, UNEXPECTED_ERROR, response.statusText);
}

/**
 * Llama al backend con `X-User` del usuario de la sesión. Ante `CURRENT_USER_REQUIRED` cierra la
 * sesión, y las guardas de rutas llevan al login.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', params, body, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json' };
  const username = useSessionStore.getState().session?.username;
  if (username) {
    headers['X-User'] = username;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, params), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiRequestError(0, NETWORK_ERROR, 'Sin conexión con el servidor');
  }

  if (!response.ok) {
    const error = await readApiError(response);
    if (error.errorCode === 'CURRENT_USER_REQUIRED') {
      useSessionStore.getState().expire();
    }
    throw error;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
