import { describe, expect, it } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { shouldRetry } from '@/lib/queryClient';

describe('shouldRetry', () => {
  it('reintenta los errores de red hasta dos veces', () => {
    const network = new ApiRequestError(0, NETWORK_ERROR, 'sin red');
    expect(shouldRetry(0, network)).toBe(true);
    expect(shouldRetry(1, network)).toBe(true);
    expect(shouldRetry(2, network)).toBe(false);
  });

  it('no reintenta un 4xx ni un 5xx con ApiError', () => {
    expect(shouldRetry(0, new ApiRequestError(404, 'ROOM_NOT_FOUND', ''))).toBe(false);
    expect(shouldRetry(0, new ApiRequestError(500, 'INTERNAL_ERROR', ''))).toBe(false);
  });
});
