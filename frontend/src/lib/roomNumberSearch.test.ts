import { describe, expect, it } from 'vitest';

import { filterByRoomNumber, roomNotFoundMessage } from '@/lib/roomNumberSearch';

const ROOMS = [{ roomNumber: '101' }, { roomNumber: '1010' }, { roomNumber: '204' }];

describe('filterByRoomNumber', () => {
  it('con la búsqueda vacía devuelve la lista completa', () => {
    expect(filterByRoomNumber(ROOMS, '')).toBe(ROOMS);
    expect(filterByRoomNumber(ROOMS, '   ')).toBe(ROOMS);
  });

  it('busca el número exacto, no los que empiezan igual', () => {
    expect(filterByRoomNumber(ROOMS, '101')).toEqual([{ roomNumber: '101' }]);
    expect(filterByRoomNumber(ROOMS, ' 204 ')).toEqual([{ roomNumber: '204' }]);
    expect(filterByRoomNumber(ROOMS, '10')).toEqual([]);
  });
});

describe('roomNotFoundMessage', () => {
  it('nombra la habitación buscada', () => {
    expect(roomNotFoundMessage(' 305 ')).toBe('No se encontró la habitación 305');
  });
});
