import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiRequest, ApiRequestError, NETWORK_ERROR, UNEXPECTED_ERROR } from '@/api/apiClient';
import { useSessionStore } from '@/stores/sessionStore';

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    useSessionStore.setState({
      session: { username: 'ana', fullName: 'Ana Ramírez', area: 'RECEPCION' },
      expired: false,
    });
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('envía X-User y los parámetros no vacíos', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiRequest('/api/rooms/inventory', {
      params: { page: 2, roomNumber: '', floor: undefined },
    });

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/rooms/inventory?page=2');
    expect((init!.headers as Record<string, string>)['X-User']).toBe('ana');
  });

  it('serializa el cuerpo como JSON en un POST', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, { roomId: 'r1' }));

    const result = await apiRequest<{ roomId: string }>('/api/rooms', {
      method: 'POST',
      body: { roomNumber: '101' },
    });

    const init = fetchMock.mock.calls[0]![1]!;
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"roomNumber":"101"}');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(result.roomId).toBe('r1');
  });

  it('convierte el ApiError en ApiRequestError con status, errorCode y details', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(409, {
        errorCode: 'ROOM_INVALID_STATE',
        message: 'La habitación está Disponible',
        timestamp: '2026-10-31T10:00:00-05:00',
        path: '/api/check-in',
        details: { currentStatus: 'Available' },
      }),
    );

    const error = await apiRequest('/api/check-in').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({
      status: 409,
      errorCode: 'ROOM_INVALID_STATE',
      details: { currentStatus: 'Available' },
    });
  });

  it('cierra la sesión ante CURRENT_USER_REQUIRED', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, { errorCode: 'CURRENT_USER_REQUIRED', message: 'Falta X-User' }),
    );

    await expect(apiRequest('/api/check-in')).rejects.toBeInstanceOf(ApiRequestError);

    expect(useSessionStore.getState().session).toBeNull();
    expect(useSessionStore.getState().expired).toBe(true);
  });

  it('usa UNEXPECTED_ERROR si la respuesta no trae ApiError', async () => {
    fetchMock.mockResolvedValue(new Response('fallo', { status: 502 }));

    await expect(apiRequest('/api/x')).rejects.toMatchObject({
      status: 502,
      errorCode: UNEXPECTED_ERROR,
    });
  });

  it('marca los errores de red con status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(apiRequest('/api/x')).rejects.toMatchObject({
      status: 0,
      errorCode: NETWORK_ERROR,
    });
  });

  it('devuelve undefined ante un 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiRequest('/api/x', { method: 'POST' })).resolves.toBeUndefined();
  });
});
