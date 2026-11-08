import { describe, expect, it } from 'vitest';

import { readConflictingReservations } from '@/api/roomService';

describe('readConflictingReservations', () => {
  const RESERVATION = {
    reservationRef: 'RES-10421',
    startDate: '2026-10-15',
    endDate: '2026-10-18',
    status: 'ACTIVE',
  };

  it('devuelve las reservas de details.reservations', () => {
    expect(readConflictingReservations({ reservations: [RESERVATION] })).toEqual([RESERVATION]);
  });

  it('descarta lo que no tiene la forma de una reserva', () => {
    expect(readConflictingReservations({})).toEqual([]);
    expect(readConflictingReservations({ reservations: 'RES-1' })).toEqual([]);
    expect(
      readConflictingReservations({ reservations: [RESERVATION, { reservationRef: 1 }, null] }),
    ).toEqual([RESERVATION]);
  });
});
