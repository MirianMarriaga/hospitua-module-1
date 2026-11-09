import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { isRoomStatus, ROOM_STATUS_LABELS } from '@/lib/labels';

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

/**
 * Estado actual de un rechazo `ROOM_INVALID_STATE` (`details.currentStatus`) con su nombre de la
 * interfaz (regla 12), para los textos "La habitación está {estado}; …" de las tablas de errores.
 * `undefined` si el error es otro.
 */
export function invalidRoomStateLabel(error: unknown): string | undefined {
  if (!(error instanceof ApiRequestError) || error.errorCode !== 'ROOM_INVALID_STATE') {
    return undefined;
  }
  const current = error.details.currentStatus;
  return typeof current === 'string' && isRoomStatus(current)
    ? ROOM_STATUS_LABELS[current]
    : 'en otro estado';
}
