import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import {
  getRoom,
  getRoomDecommission,
  reactivateRoom,
  type ReactivationResultDto,
  type RoomDecommissionInfoDto,
} from '@/api/roomService';
import { ReactivationPage } from '@/pages/administracion/reactivation/ReactivationPage';
import { LocationProbe, renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/roomService', () => ({
  getRoom: vi.fn(),
  getRoomDecommission: vi.fn(),
  reactivateRoom: vi.fn(),
}));

const INACTIVE_ROOM_INFO: RoomDecommissionInfoDto = {
  roomId: 'room-204',
  roomNumber: '204',
  floor: 2,
  categoryRoom: 'BOUTIQUE',
  status: 'Inactive',
  decommissionId: 'dec-uuid-1',
  decommissionDateTime: '2026-10-08T22:30:00-05:00',
  reasonCategory: 'ADMINISTRATIVE_DECISION',
  reasonDetail: 'Ajuste operativo temporal',
};

describe('ReactivationPage', () => {
  beforeEach(() => {
    vi.mocked(getRoom).mockReset();
    vi.mocked(getRoomDecommission).mockReset();
    vi.mocked(reactivateRoom).mockReset();
    vi.mocked(getRoomDecommission).mockResolvedValue(INACTIVE_ROOM_INFO);
  });

  it('flujo feliz: confirmación y resultado exitoso como disponible', async () => {
    const successResult: ReactivationResultDto = {
      roomId: 'room-204',
      roomNumber: '204',
      floor: 2,
      categoryRoom: 'BOUTIQUE',
      status: 'Available',
      alreadyReactivated: false,
    };
    vi.mocked(reactivateRoom).mockResolvedValue(successResult);

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    expect(await screen.findByText('¿Reactivar la habitación 204?')).toBeInTheDocument();
    expect(screen.getByText('Decisión administrativa')).toBeInTheDocument();
    expect(screen.getByText('Ajuste operativo temporal')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reactivación' }));

    expect(await screen.findByText('Habitación 204 disponible')).toBeInTheDocument();
    expect(screen.getByText('Ya puede recibir reservas y estancias.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inventario' })).toBeInTheDocument();
  });

  it('el resultado se queda aunque la baja ya no se pueda consultar después de reactivar', async () => {
    vi.mocked(reactivateRoom).mockImplementation(() => {
      // Ya reactivada, la baja vigente responde 409: la habitación no está inactiva.
      vi.mocked(getRoomDecommission).mockRejectedValue(
        new ApiRequestError(409, 'ROOM_INVALID_STATE', 'No está inactiva'),
      );
      return Promise.resolve({
        roomId: 'room-204',
        roomNumber: '204',
        floor: 2,
        categoryRoom: 'BOUTIQUE',
        status: 'Available',
        alreadyReactivated: false,
      });
    });

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar reactivación' }));

    expect(await screen.findByText('Habitación 204 disponible')).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.getByText('Habitación 204 disponible')).toBeInTheDocument();
    expect(screen.queryByText('Habitación no encontrada')).not.toBeInTheDocument();
    expect(getRoomDecommission).toHaveBeenCalledTimes(1);
  });

  it('si alreadyReactivated es true, muestra el resultado como éxito normal sin alarmas', async () => {
    const alreadyResult: ReactivationResultDto = {
      roomId: 'room-204',
      roomNumber: '204',
      floor: 2,
      categoryRoom: 'BOUTIQUE',
      status: 'Available',
      alreadyReactivated: true,
    };
    vi.mocked(reactivateRoom).mockResolvedValue(alreadyResult);

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    await screen.findByText('¿Reactivar la habitación 204?');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reactivación' }));

    expect(await screen.findByText('Habitación 204 disponible')).toBeInTheDocument();
    expect(
      screen.getByText('La habitación ya había sido reactivada; no fue necesario hacer cambios.'),
    ).toBeInTheDocument();
  });

  it('si la habitación no está inactiva inicialmente, muestra aviso', async () => {
    vi.mocked(getRoomDecommission).mockResolvedValue({
      ...INACTIVE_ROOM_INFO,
      status: 'Available',
    });

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    expect(
      await screen.findByText('No se puede marcar como disponible la habitación 204'),
    ).toBeInTheDocument();
    expect(screen.getByText(/Solo se pueden reactivar habitaciones inactivas/)).toBeInTheDocument();
  });

  it('si la baja responde 409 ROOM_INVALID_STATE, avisa que no está inactiva con su número y estado (FR-006)', async () => {
    vi.mocked(getRoomDecommission).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'No está inactiva', {
        currentStatus: 'Available',
      }),
    );
    vi.mocked(getRoom).mockResolvedValue({
      roomId: 'room-204',
      roomNumber: '204',
      floor: 2,
      categoryRoom: 'BOUTIQUE',
      maxCapacity: 2,
      baseRate: 320000,
      status: 'Available',
      version: 3,
    });

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    expect(
      await screen.findByText('No se puede marcar como disponible la habitación 204'),
    ).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'La habitación está en estado Disponible. Solo se pueden reactivar habitaciones inactivas.',
    );
    expect(screen.queryByText('Habitación no encontrada')).not.toBeInTheDocument();
    expect(getRoom).toHaveBeenCalledWith('room-204');
    expect(
      screen.queryByRole('button', { name: 'Confirmar reactivación' }),
    ).not.toBeInTheDocument();
  });

  it('si no se puede leer la habitación, el aviso usa el estado del 409 sin número', async () => {
    vi.mocked(getRoomDecommission).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'No está inactiva', {
        currentStatus: 'Occupied',
      }),
    );
    vi.mocked(getRoom).mockRejectedValue(new ApiRequestError(500, 'INTERNAL_ERROR', 'falló'));

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    expect(
      await screen.findByText('No se puede marcar como disponible esta habitación'),
    ).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('La habitación está en estado Ocupada.');
  });

  it('si la habitación no existe (404), dice que no se encontró y no la consulta', async () => {
    vi.mocked(getRoomDecommission).mockRejectedValue(
      new ApiRequestError(404, 'ROOM_NOT_FOUND', 'No se encontró la habitación.'),
    );

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-999/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    expect(await screen.findByText('Habitación no encontrada')).toBeInTheDocument();
    expect(getRoom).not.toHaveBeenCalled();
  });

  it('error 409 ROOM_INVALID_STATE informa que la habitación ya no está inactiva', async () => {
    vi.mocked(reactivateRoom).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'Estado inválido', {
        currentStatus: 'Occupied',
      }),
    );

    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    await screen.findByText('¿Reactivar la habitación 204?');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reactivación' }));

    expect(
      await screen.findByText(/La habitación 204 ya no está inactiva \(estado actual:/),
    ).toBeInTheDocument();
  });
  it('la confirmación muestra la habitación y la baja, y "Cancelar" vuelve sin enviar nada', async () => {
    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
      extraRoutes: [
        { path: '/administracion/inventario', element: <LocationProbe label="inventario" /> },
      ],
    });

    await screen.findByText('¿Reactivar la habitación 204?');
    expect(screen.getByText('Piso 2')).toBeInTheDocument();
    expect(screen.getByText('Boutique')).toBeInTheDocument();
    expect(screen.getByText('Inactiva')).toBeInTheDocument();
    expect(screen.getByText('08-10-2026')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: 'Cancelar' }));

    expect(await screen.findByTestId('location')).toHaveTextContent('inventario');
    expect(reactivateRoom).not.toHaveBeenCalled();
  });

  it('sin detalle de la baja no muestra el campo Detalle', async () => {
    vi.mocked(getRoomDecommission).mockResolvedValue({ ...INACTIVE_ROOM_INFO, reasonDetail: null });
    renderWithProviders(<ReactivationPage />, {
      route: '/administracion/habitaciones/room-204/reactivacion',
      path: '/administracion/habitaciones/:roomId/reactivacion',
    });

    await screen.findByText('¿Reactivar la habitación 204?');
    expect(screen.getByText('Decisión administrativa')).toBeInTheDocument();
    expect(screen.queryByText('Detalle')).not.toBeInTheDocument();
  });
});
