import { describe, expect, it } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { invalidRoomStateLabel } from '@/lib/errorMessages';

describe('invalidRoomStateLabel', () => {
  it('da el nombre del estado en español de un ROOM_INVALID_STATE', () => {
    const error = new ApiRequestError(409, 'ROOM_INVALID_STATE', 'estado', {
      currentStatus: 'DisabledForRepairs',
    });
    expect(invalidRoomStateLabel(error)).toBe('Inhabilitada por reparaciones');
  });

  it('con un estado desconocido o ausente dice "en otro estado"', () => {
    expect(invalidRoomStateLabel(new ApiRequestError(409, 'ROOM_INVALID_STATE', 'estado'))).toBe(
      'en otro estado',
    );
  });

  it('con otro error no da nada', () => {
    expect(invalidRoomStateLabel(new ApiRequestError(404, 'ROOM_NOT_FOUND', 'no'))).toBeUndefined();
    expect(invalidRoomStateLabel(new Error('red'))).toBeUndefined();
  });
});
