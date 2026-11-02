import type { DecommissionReason } from '@/api/roomService';
import type { DocumentType, RoomCategory, RoomStatus, StaffArea } from '@/api/types';

// Etiquetas en español (regla 12 del plan base). Coinciden con los displayName del backend.

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  Available: 'Disponible',
  Reserved: 'Reservada',
  Occupied: 'Ocupada',
  PendingCleaning: 'Pendiente de limpieza',
  InCleaning: 'En limpieza',
  DisabledForRepairs: 'Inhabilitada por reparaciones',
  TechnicalBlock: 'Bloqueo técnico',
  Inactive: 'Inactiva',
};

export const ROOM_STATUSES = Object.keys(ROOM_STATUS_LABELS) as RoomStatus[];

export const ROOM_CATEGORY_LABELS: Record<RoomCategory, string> = {
  SENCILLA: 'Sencilla',
  DOBLE: 'Doble',
  SUITE: 'Suite',
  BOUTIQUE: 'Boutique',
};

export const ROOM_CATEGORIES = Object.keys(ROOM_CATEGORY_LABELS) as RoomCategory[];

export const DECOMMISSION_REASON_LABELS: Record<DecommissionReason, string> = {
  PERMANENT_REMODELING: 'Remodelación permanente',
  FLOOR_CLOSURE: 'Cierre definitivo de piso',
  ADMINISTRATIVE_DECISION: 'Decisión administrativa',
  OTHER: 'Otro',
};

export const DECOMMISSION_REASONS = Object.keys(DECOMMISSION_REASON_LABELS) as DecommissionReason[];

export const RESERVATION_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente de confirmación',
  ACTIVE: 'Confirmada',
  IN_PROGRESS: 'En curso',
};

export const STAFF_AREA_LABELS: Record<StaffArea, string> = {
  RECEPCION: 'Recepción',
  ADMINISTRACION: 'Administración',
  GERENCIA: 'Gerencia',
  LIMPIEZA: 'Limpieza',
  MANTENIMIENTO: 'Mantenimiento',
};

export const STAFF_ROLE_LABELS: Record<StaffArea, string> = {
  RECEPCION: 'Recepcionista',
  ADMINISTRACION: 'Administrador',
  GERENCIA: 'Gerente',
  LIMPIEZA: 'Personal de limpieza',
  MANTENIMIENTO: 'Personal de mantenimiento',
};

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  RC: 'Registro civil',
  TI: 'Tarjeta de identidad',
  CC: 'Cédula de ciudadanía',
  CE: 'Cédula de extranjería',
  PAS: 'Pasaporte',
  NIT: 'NIT',
};

export const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[];

const SOURCE_LABELS: Record<string, string> = {
  DIRECT: 'Directa',
  DIRECTA: 'Directa',
  BOOKING: 'Booking',
  EXPEDIA: 'Expedia',
  AIRBNB: 'Airbnb',
};

/** Fuente de la reserva tal como la envía Módulo 2; las desconocidas se muestran sin cambio. */
export function sourceLabel(source: string): string {
  return SOURCE_LABELS[source.toUpperCase()] ?? source;
}

/** Tipo de habitación que llega como texto (copia local de reservas). */
export function categoryLabel(category: string): string {
  return ROOM_CATEGORY_LABELS[category.toUpperCase() as RoomCategory] ?? category;
}

export function isRoomStatus(value: string | null): value is RoomStatus {
  return value !== null && value in ROOM_STATUS_LABELS;
}

export function isRoomCategory(value: string | null): value is RoomCategory {
  return value !== null && value in ROOM_CATEGORY_LABELS;
}
