import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import {
  decommissionRoom,
  getRoom,
  type Room,
  type RoomDecommissionDto,
} from '@/api/roomService';
import { DecommissionPage } from '@/pages/administracion/decommission/DecommissionPage';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/roomService', () => ({
  getRoom: vi.fn(),
  decommissionRoom: vi.fn(),
}));

const AVAILABLE_ROOM: Room = {
  roomId: 'room-101',
  roomNumber: '101',
  floor: 1,
  categoryRoom: 'SENCILLA',
  maxCapacity: 1,
  baseRate: 180000,
  status: 'Available',
  version: 1,
};

describe('DecommissionPage', () => {
  beforeEach(() => {
    vi.mocked(getRoom).mockReset();
    vi.mocked(decommissionRoom).mockReset();
    vi.mocked(getRoom).mockResolvedValue(AVAILABLE_ROOM);
  });

  it('el motivo "Otro" sin detalle bloquea el avance al paso de confirmación', async () => {
    renderWithProviders(<DecommissionPage />, {
      route: '/administracion/habitaciones/room-101/baja',
      path: '/administracion/habitaciones/:roomId/baja',
    });

    expect(await screen.findByText(/1\. Motivo de la baja/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/Motivo de la baja/), 'OTHER');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Escribe el motivo de la baja.')).toBeInTheDocument();
    expect(decommissionRoom).not.toHaveBeenCalled();
    expect(screen.queryByText(/2\. ¿Confirmas la baja/)).not.toBeInTheDocument();
  });

  it('flujo feliz: motivo -> confirmar -> verificando -> resultado con habitación inactiva', async () => {
    const decommissionResult: RoomDecommissionDto = {
      decommissionId: 'dec-1',
      roomId: 'room-101',
      roomNumber: '101',
      floor: 1,
      categoryRoom: 'SENCILLA',
      status: 'Inactive',
      reasonCategory: 'PERMANENT_REMODELING',
      reasonDetail: null,
      decommissionDateTime: new Date().toISOString(),
    };
    vi.mocked(decommissionRoom).mockResolvedValue(decommissionResult);

    renderWithProviders(<DecommissionPage />, {
      route: '/administracion/habitaciones/room-101/baja',
      path: '/administracion/habitaciones/:roomId/baja',
    });

    expect(await screen.findByText(/1\. Motivo de la baja/)).toBeInTheDocument();

    // Seleccionar motivo
    await userEvent.selectOptions(
      screen.getByLabelText(/Motivo de la baja/),
      'PERMANENT_REMODELING',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    // Paso 2: confirmación
    expect(
      await screen.findByText('2. ¿Confirmas la baja de la habitación 101?'),
    ).toBeInTheDocument();
    expect(screen.getByText('Remodelación permanente')).toBeInTheDocument();

    // Confirmar
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    // Paso 4: resultado exitoso
    expect(await screen.findByText('Habitación 101 dada de baja')).toBeInTheDocument();
    expect(screen.getByText('Ya no aparece entre las habitaciones disponibles.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inventario' })).toBeInTheDocument();
  });

  it('conflicto de reservas (409 RESERVATION_CONFLICT) muestra lista de reservas en conflicto', async () => {
    vi.mocked(decommissionRoom).mockRejectedValue(
      new ApiRequestError(409, 'RESERVATION_CONFLICT', 'Conflicto de reservas', {
        reservations: [
          {
            reservationRef: 'RES-10421',
            startDate: '2026-10-15',
            endDate: '2026-10-18',
            status: 'ACTIVE',
          },
        ],
      }),
    );

    renderWithProviders(<DecommissionPage />, {
      route: '/administracion/habitaciones/room-101/baja',
      path: '/administracion/habitaciones/:roomId/baja',
    });

    await screen.findByText(/1\. Motivo de la baja/);
    await userEvent.selectOptions(screen.getByLabelText(/Motivo de la baja/), 'FLOOR_CLOSURE');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    expect(
      await screen.findByText('No se puede dar de baja la habitación 101'),
    ).toBeInTheDocument();
    expect(screen.getByText(/La habitación tiene una reserva vigente/)).toBeInTheDocument();
    expect(screen.getByText('RES-10421')).toBeInTheDocument();
  });

  it('fallo de reservas (424 MODULE_2_UNAVAILABLE) ofrece botón de Reintentar', async () => {
    vi.mocked(decommissionRoom).mockRejectedValueOnce(
      new ApiRequestError(424, 'MODULE_2_UNAVAILABLE', 'Módulo 2 no disponible'),
    );

    renderWithProviders(<DecommissionPage />, {
      route: '/administracion/habitaciones/room-101/baja',
      path: '/administracion/habitaciones/:roomId/baja',
    });

    await screen.findByText(/1\. Motivo de la baja/);
    await userEvent.selectOptions(
      screen.getByLabelText(/Motivo de la baja/),
      'ADMINISTRATIVE_DECISION',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    expect(await screen.findByText('No se pudo completar la baja')).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: 'Reintentar' });
    expect(retryBtn).toBeInTheDocument();

    // Reintentar envía la misma petición con el mismo motivo
    const successResult: RoomDecommissionDto = {
      decommissionId: 'dec-1',
      roomId: 'room-101',
      roomNumber: '101',
      floor: 1,
      categoryRoom: 'SENCILLA',
      status: 'Inactive',
      reasonCategory: 'ADMINISTRATIVE_DECISION',
      reasonDetail: null,
      decommissionDateTime: new Date().toISOString(),
    };
    vi.mocked(decommissionRoom).mockResolvedValueOnce(successResult);

    await userEvent.click(retryBtn);

    expect(await screen.findByText('Habitación 101 dada de baja')).toBeInTheDocument();
    expect(decommissionRoom).toHaveBeenCalledTimes(2);
  });

  it('cambio de estado (409 ROOM_INVALID_STATE) informa que la habitación no está disponible', async () => {
    vi.mocked(decommissionRoom).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'Estado inválido', {
        currentStatus: 'Occupied',
      }),
    );

    renderWithProviders(<DecommissionPage />, {
      route: '/administracion/habitaciones/room-101/baja',
      path: '/administracion/habitaciones/:roomId/baja',
    });

    await screen.findByText(/1\. Motivo de la baja/);
    await userEvent.selectOptions(screen.getByLabelText(/Motivo de la baja/), 'FLOOR_CLOSURE');
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    expect(
      await screen.findByText(/La habitación tiene un huésped activo; debe estar Disponible/),
    ).toBeInTheDocument();
  });
});
