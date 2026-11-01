// Tipos de los DTOs del backend (documentos/PLAN/). Las fechas de negocio llegan como
// YYYY-MM-DD y las marcas de tiempo con offset -05:00; ambas se quedan como string.

export type RoomStatus =
  | 'Available'
  | 'Reserved'
  | 'Occupied'
  | 'PendingCleaning'
  | 'InCleaning'
  | 'DisabledForRepairs'
  | 'TechnicalBlock'
  | 'Inactive';

export type RoomCategory = 'SENCILLA' | 'DOBLE' | 'SUITE' | 'BOUTIQUE';

export type StaffArea = 'RECEPCION' | 'ADMINISTRACION' | 'GERENCIA' | 'LIMPIEZA' | 'MANTENIMIENTO';

export type DocumentType = 'RC' | 'TI' | 'CC' | 'CE' | 'PAS' | 'NIT';

export interface ApiError {
  errorCode: string;
  message: string;
  timestamp: string;
  path: string;
  details?: Record<string, unknown>;
}

export interface PagedResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
