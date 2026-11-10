import { z } from 'zod';

import { ApiRequestError } from '@/api/apiClient';
import type { EditStaffRequest, RegisterStaffRequest, StaffAccount } from '@/api/staffService';
import type { StaffArea } from '@/api/types';
import { isStaffArea } from '@/lib/labels';

// Mismas reglas y textos que `RegisterStaffRequest` y `EditStaffRequest` del backend y que las
// tablas de errores de plan-gestionar-personal.md (FR-002 a FR-004, FR-008).

const USERNAME = /^[A-Za-z0-9._]{3,30}$/;
const MIN_PASSWORD_LENGTH = 8;
/** Límite de BCrypt, que se mide en bytes: una tilde o una ñ ocupan dos. */
const MAX_PASSWORD_BYTES = 72;
const MAX_FULL_NAME_LENGTH = 150;

const PASSWORD_RULE = 'La contraseña debe tener al menos 8 caracteres.';
const PASSWORD_LIMIT_RULE = 'La contraseña admite máximo 72 caracteres.';

function fitsPasswordLimit(password: string): boolean {
  return new TextEncoder().encode(password).length <= MAX_PASSWORD_BYTES;
}

const fullName = z
  .string()
  .trim()
  .min(1, 'Ingresa el nombre completo.')
  .max(MAX_FULL_NAME_LENGTH, 'El nombre completo admite máximo 150 caracteres.');

const area = z
  .string()
  .min(1, 'Selecciona el área.')
  .refine((value): boolean => isStaffArea(value), 'Selecciona un área válida.');

export const registerStaffSchema = z.object({
  username: z
    .string()
    .trim()
    .min(1, 'Ingresa el usuario.')
    .regex(
      USERNAME,
      'El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo.',
    ),
  password: z
    .string()
    .refine((value) => value.trim() !== '', 'Ingresa la contraseña.')
    .min(MIN_PASSWORD_LENGTH, PASSWORD_RULE)
    .refine(fitsPasswordLimit, PASSWORD_LIMIT_RULE),
  fullName,
  area,
});

export type RegisterStaffValues = z.infer<typeof registerStaffSchema>;

export const EMPTY_STAFF: RegisterStaffValues = {
  username: '',
  password: '',
  fullName: '',
  area: '',
};

/** El usuario viaja como lo guarda el backend: sin espacios en los extremos y en minúsculas. */
export function normalizeUsername(username: string): string {
  return username.trim().toLowerCase();
}

export function toRegisterStaffRequest(values: RegisterStaffValues): RegisterStaffRequest {
  return {
    username: normalizeUsername(values.username),
    password: values.password,
    fullName: values.fullName.trim(),
    area: values.area as StaffArea,
  };
}

/** Al editar, la contraseña es opcional: en blanco conserva la actual (FR-008, esc. 7). */
export const editStaffSchema = z.object({
  password: z
    .string()
    .refine((value) => value.trim() === '' || value.length >= MIN_PASSWORD_LENGTH, PASSWORD_RULE)
    .refine(fitsPasswordLimit, PASSWORD_LIMIT_RULE),
  fullName,
  area,
});

export type EditStaffValues = z.infer<typeof editStaffSchema>;

/** Valores iniciales de la edición: la contraseña nunca se precarga. */
export function toEditStaffValues(account: StaffAccount): EditStaffValues {
  return { password: '', fullName: account.fullName, area: account.area };
}

export function toEditStaffRequest(values: EditStaffValues, version: number): EditStaffRequest {
  const request: EditStaffRequest = {
    version,
    fullName: values.fullName.trim(),
    area: values.area as StaffArea,
  };
  if (values.password.trim() !== '') {
    request.password = values.password;
  }
  return request;
}

const GENERIC_FIELD_ERROR = 'Revisa este campo.';

/**
 * Campos que el backend rechazó con `VALIDATION_ERROR`: `details.fields` cuando faltan varios y
 * `details.field` cuando uno incumple su regla. Solo devuelve los que el formulario conoce.
 */
export function rejectedFields<Field extends string>(
  error: unknown,
  known: readonly Field[],
): Field[] {
  if (!(error instanceof ApiRequestError) || error.errorCode !== 'VALIDATION_ERROR') {
    return [];
  }
  const { field, fields } = error.details;
  const names = Array.isArray(fields) ? fields : [field];
  return known.filter((name) => names.includes(name));
}

/**
 * Texto de un campo que rechazó el backend: el de la regla que el formulario también ve incumplida
 * o, si aquí pasa (las reglas se desalinearon), uno genérico. Nunca el `message` técnico.
 */
export function fieldErrorText(
  schema: z.ZodType,
  values: Record<string, unknown>,
  field: string,
): string {
  const result = schema.safeParse(values);
  if (result.success) {
    return GENERIC_FIELD_ERROR;
  }
  return (
    result.error.issues.find((issue) => issue.path[0] === field)?.message ?? GENERIC_FIELD_ERROR
  );
}
