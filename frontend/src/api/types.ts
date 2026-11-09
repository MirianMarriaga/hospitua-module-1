// Tipos de los DTOs del backend. Salen de `schema.d.ts`, que genera `npm run api:types` desde el
// contrato `documentos/contrato/openapi.json` (lote C02); aquí solo se les da el nombre que usan
// los servicios. Las fechas de negocio llegan como YYYY-MM-DD y las marcas de tiempo con offset
// -05:00; ambas se quedan como string.

import type { components } from '@/api/schema';

type Schemas = components['schemas'];

/** Estado de la habitación (`RoomStatus` del backend). */
export type RoomStatus = NonNullable<Schemas['RoomDto']['status']>;

/** Categoría de la habitación (`RoomCategory` del backend). */
export type RoomCategory = NonNullable<Schemas['RoomDto']['categoryRoom']>;

/** Área del personal (`StaffArea` del backend). */
export type StaffArea = NonNullable<Schemas['StaffAccountDto']['area']>;

/**
 * Tipo de documento del huésped. El contrato lo publica como `string` sin `enum` (el backend lo
 * valida en el dominio), así que la lista se mantiene a mano y debe coincidir con
 * `CheckInGuest.DOCUMENT_TYPES` del backend.
 */
export type DocumentType = 'RC' | 'TI' | 'CC' | 'CE' | 'PAS' | 'NIT';

/**
 * Cuerpo de error del backend ("Formato Estándar de Error" del plan base). En el contrato todos
 * sus campos son opcionales; el backend siempre envía `errorCode`, `message`, `timestamp` y `path`.
 */
export type ApiError = Required<Omit<Schemas['ApiError'], 'details'>> &
  Pick<Schemas['ApiError'], 'details'>;

/**
 * Página de resultados. El contrato tiene una por tipo (`PagedResultDtoRoomDto`, …); esta forma
 * genérica es la misma y se comprueba contra ellas en `types.test.ts`.
 */
export interface PagedResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
