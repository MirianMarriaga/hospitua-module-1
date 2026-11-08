import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { usePanelQuery } from '@/hooks/usePanelQuery';

function wrapper(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe('usePanelQuery', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('adapta la respuesta con select y vuelve a consultar cada 60 segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const fetchPanel = vi.fn().mockResolvedValue({ rooms: ['101', '204'] });

    const { result } = renderHook(
      () => usePanelQuery(['panel'], fetchPanel, (data: { rooms: string[] }) => data.rooms.length),
      { wrapper: wrapper(client) },
    );
    await waitFor(() => expect(result.current.data).toBe(2));
    expect(fetchPanel).toHaveBeenCalledTimes(1);

    await act(() => vi.advanceTimersByTimeAsync(60_000));
    expect(fetchPanel).toHaveBeenCalledTimes(2);
  });

  it('vuelve a consultar al montarse otra vez aunque los datos estén frescos', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    });
    const fetchPanel = vi.fn().mockResolvedValue('ok');

    const first = renderHook(() => usePanelQuery(['panel'], fetchPanel), {
      wrapper: wrapper(client),
    });
    await waitFor(() => expect(first.result.current.data).toBe('ok'));
    first.unmount();

    renderHook(() => usePanelQuery(['panel'], fetchPanel), { wrapper: wrapper(client) });
    await waitFor(() => expect(fetchPanel).toHaveBeenCalledTimes(2));
  });
});
