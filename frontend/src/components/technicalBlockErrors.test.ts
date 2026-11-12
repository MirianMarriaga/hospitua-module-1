import { describe, expect, it } from 'vitest';

import type { RoomStatus } from '@/api/types';
import { invalidStateMessage } from '@/components/technicalBlockErrors';

describe('invalidStateMessage', () => {
  it.each<[RoomStatus, string]>([
    ['Reserved', 'Habitación reservada hoy. Elige otra fecha de inicio.'],
    ['Occupied', 'Habitación ocupada hoy. Elige otra fecha de inicio.'],
    ['PendingCleaning', 'Habitación pendiente de limpieza hoy. Elige otra fecha de inicio.'],
    ['InCleaning', 'Habitación en limpieza hoy. Elige otra fecha de inicio.'],
    [
      'DisabledForRepairs',
      'Habitación inhabilitada por reparaciones hoy. Elige otra fecha de inicio.',
    ],
    ['TechnicalBlock', 'Habitación en bloqueo técnico hoy. Elige otra fecha de inicio.'],
    ['Inactive', 'La habitación está dada de baja; no se puede programar mantenimiento.'],
  ])('con %s dice "%s"', (status, message) => {
    expect(invalidStateMessage(status)).toBe(message);
  });
});
