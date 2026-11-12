import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ELAPSED_REFRESH_MS, useElapsedMinutes } from '@/hooks/useElapsedMinutes';

describe('useElapsedMinutes', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-10T10:00:00-05:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('cuenta los minutos completos desde el inicio y se actualiza solo', () => {
    const { result } = renderHook(() => useElapsedMinutes('2026-11-10T09:15:30-05:00'));
    expect(result.current).toBe(44);

    act(() => {
      vi.advanceTimersByTime(ELAPSED_REFRESH_MS * 4);
    });

    expect(result.current).toBe(45);
  });

  it.each([
    ['sin fecha', undefined],
    ['con una fecha ilegible', 'no-es-fecha'],
    ['con una fecha futura', '2026-11-10T11:00:00-05:00'],
  ])('devuelve 0 %s', (_case, start) => {
    const { result } = renderHook(() => useElapsedMinutes(start));
    expect(result.current).toBe(0);
  });
});
