import { z } from 'zod';

import type { CheckInGuest, CheckInHolder } from '@/api/checkInService';
import type { DocumentType } from '@/api/types';
import { DOCUMENT_TYPES } from '@/lib/labels';

export const COLOMBIA = 'Colombia';

/** Mismo criterio que `CheckInGuest` del backend: extranjero si la nacionalidad no es Colombia. */
export function isForeigner(nationality: string): boolean {
  const value = nationality.trim();
  return value !== '' && value.toLowerCase() !== COLOMBIA.toLowerCase();
}

// Textos por campo; también se usan cuando el backend marca un campo con details.field.
export const GUEST_FIELD_TEXTS: Record<string, string> = {
  firstName: 'Los nombres son obligatorios.',
  lastName: 'Los apellidos son obligatorios.',
  documentType: 'Selecciona un tipo de documento de la lista.',
  documentNumber: 'El número de documento es obligatorio.',
  nationality: 'La nacionalidad es obligatoria.',
  birthDate: 'La fecha de nacimiento es obligatoria y debe ser anterior a hoy.',
  originPlace: 'El lugar de procedencia es obligatorio para huéspedes extranjeros.',
  destinationPlace: 'El lugar de destino es obligatorio para huéspedes extranjeros.',
};

export const GUEST_FIELDS = Object.keys(GUEST_FIELD_TEXTS) as (keyof GuestValues)[];

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function text(max: number, message: string) {
  return z.string().trim().min(1, message).max(max, `El campo admite máximo ${max} caracteres.`);
}

export function createGuestsSchema(today: string) {
  const guest = z
    .object({
      firstName: text(100, GUEST_FIELD_TEXTS.firstName!),
      lastName: text(100, GUEST_FIELD_TEXTS.lastName!),
      documentType: z
        .string()
        .refine(
          (value) => (DOCUMENT_TYPES as string[]).includes(value),
          GUEST_FIELD_TEXTS.documentType!,
        ),
      documentNumber: text(50, GUEST_FIELD_TEXTS.documentNumber!),
      nationality: text(100, GUEST_FIELD_TEXTS.nationality!),
      // Las fechas ISO se comparan como texto, sin pasar por la zona del navegador.
      birthDate: z
        .string()
        .refine((value) => ISO_DATE.test(value) && value < today, GUEST_FIELD_TEXTS.birthDate!),
      originPlace: z.string().max(255, 'El campo admite máximo 255 caracteres.'),
      destinationPlace: z.string().max(255, 'El campo admite máximo 255 caracteres.'),
      isReservationGuest: z.boolean(),
    })
    .superRefine((value, context) => {
      if (!isForeigner(value.nationality)) {
        return;
      }
      for (const field of ['originPlace', 'destinationPlace'] as const) {
        if (!value[field].trim()) {
          context.addIssue({ code: 'custom', path: [field], message: GUEST_FIELD_TEXTS[field]! });
        }
      }
    });

  return z.object({
    guests: z.array(guest).superRefine((guests, context) => {
      const holders = guests.filter((item) => item.isReservationGuest).length;
      if (holders > 1) {
        context.addIssue({
          code: 'custom',
          message: 'Solo un huésped puede ser el titular de la reserva.',
        });
      }
      const seen = new Set<string>();
      guests.forEach((item, index) => {
        const key = `${item.documentType}|${item.documentNumber.trim().toLowerCase()}`;
        if (item.documentNumber.trim() && seen.has(key)) {
          context.addIssue({
            code: 'custom',
            path: [index, 'documentNumber'],
            message: 'Otro huésped de esta habitación ya tiene este documento.',
          });
        }
        seen.add(key);
      });
    }),
  });
}

export type GuestsValues = z.infer<ReturnType<typeof createGuestsSchema>>;
export type GuestValues = GuestsValues['guests'][number];

export function holderGuest(holder: CheckInHolder): GuestValues {
  return {
    firstName: holder.firstName,
    lastName: holder.lastName,
    documentType: holder.documentType,
    documentNumber: holder.documentNumber,
    nationality: holder.nationality || COLOMBIA,
    birthDate: '',
    originPlace: '',
    destinationPlace: '',
    isReservationGuest: true,
  };
}

export function emptyGuest(): GuestValues {
  return {
    firstName: '',
    lastName: '',
    documentType: 'CC',
    documentNumber: '',
    nationality: COLOMBIA,
    birthDate: '',
    originPlace: '',
    destinationPlace: '',
    isReservationGuest: false,
  };
}

/** Bloques iniciales: el primero es el titular precargado y el resto quedan vacíos. */
export function initialGuests(holder: CheckInHolder, guestCount: number): GuestValues[] {
  return [holderGuest(holder), ...Array.from({ length: Math.max(guestCount - 1, 0) }, emptyGuest)];
}

export function toCheckInGuest(values: GuestValues): CheckInGuest {
  const foreigner = isForeigner(values.nationality);
  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    documentType: values.documentType as DocumentType,
    documentNumber: values.documentNumber.trim(),
    nationality: values.nationality.trim(),
    birthDate: values.birthDate,
    originPlace: foreigner ? values.originPlace.trim() : undefined,
    destinationPlace: foreigner ? values.destinationPlace.trim() : undefined,
    isReservationGuest: values.isReservationGuest,
  };
}
