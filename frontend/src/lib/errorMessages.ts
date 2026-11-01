import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';

// Textos que ve el usuario según el errorCode. La interfaz nunca muestra el `message` técnico.

const COMMON_MESSAGES: Record<string, string> = {
  [NETWORK_ERROR]: 'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.',
  CURRENT_USER_REQUIRED: 'Tu sesión terminó. Inicia sesión de nuevo.',
  CONCURRENT_UPDATE: 'Alguien más cambió esta habitación al mismo tiempo. Intenta de nuevo.',
  VALIDATION_ERROR: 'Revisa los datos ingresados.',
};

const FALLBACK_MESSAGE = 'No pudimos completar la operación. Intenta de nuevo.';

export type ErrorTexts = Partial<Record<string, string>>;

export function errorCodeOf(error: unknown): string | undefined {
  return error instanceof ApiRequestError ? error.errorCode : undefined;
}

/** Texto para el usuario: primero el de la pantalla, después el común y, si no, uno genérico. */
export function errorMessage(error: unknown, texts: ErrorTexts = {}): string {
  const code = errorCodeOf(error);
  if (code === undefined) {
    return FALLBACK_MESSAGE;
  }
  return texts[code] ?? COMMON_MESSAGES[code] ?? FALLBACK_MESSAGE;
}
