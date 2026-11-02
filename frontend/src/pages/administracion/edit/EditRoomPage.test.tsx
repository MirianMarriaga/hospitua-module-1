import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { editRoom, getRoom, type EditRoomResultDto, type Room } from '@/api/roomService';
import { EditRoomPage } from '@/pages/administracion/edit/EditRoomPage';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/roomService', () => ({
  getRoom: vi.fn(),
  editRoom: vi.fn(),
}));

const INITIAL_ROOM: Room = {
  roomId: 'room-101',
  roomNumber: '101',
  floor: 1,
  categoryRoom: 'SENCILLA',
  maxCapacity: 1,
  baseRate: 180000,
  status: 'Available',
  version: 3,
};

describe('EditRoomPage', () => {
  beforeEach(() => {
    vi.mocked(getRoom).mockReset();
    vi.mocked(editRoom).mockReset();
    vi.mocked(getRoom).mockResolvedValue(INITIAL_ROOM);
  });

  it('flujo feliz: edita un dato y muestra tabla Antes / Ahora en pantalla de éxito', async () => {
    const editResult: EditRoomResultDto = {
      changed: true,
      changes: [
        {
          field: 'baseRate',
          previousValue: 180000,
          newValue: 210000,
        },
      ],
      room: {
        ...INITIAL_ROOM,
        baseRate: 210000,
        version: 4,
      },
    };
    vi.mocked(editRoom).mockResolvedValue(editResult);

    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    expect(await screen.findByText('Editar habitación 101')).toBeInTheDocument();

    const rateInput = screen.getByLabelText(/Tarifa base por noche/);
    await userEvent.clear(rateInput);
    await userEvent.type(rateInput, '210000');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Habitación 101 actualizada')).toBeInTheDocument();
    expect(screen.getByText('Los cambios ya se reflejan en el inventario.')).toBeInTheDocument();
    expect(screen.getAllByText('Tarifa base').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('link', { name: 'Volver al inventario' })).toBeInTheDocument();
  });

  it('si no se modificó ningún dato, avisa localmente sin llamar al backend', async () => {
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    expect(await screen.findByText('Editar habitación 101')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('No has modificado ningún dato.')).toBeInTheDocument();
    expect(editRoom).not.toHaveBeenCalled();
  });

  it('si el backend responde changed: false, muestra mensaje correspondiente', async () => {
    const noChangeResult: EditRoomResultDto = {
      changed: false,
      changes: [],
      room: INITIAL_ROOM,
    };
    vi.mocked(editRoom).mockResolvedValue(noChangeResult);

    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    const rateInput = screen.getByLabelText(/Tarifa base por noche/);
    await userEvent.clear(rateInput);
    await userEvent.type(rateInput, '200000');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('No se modificó ningún dato.')).toBeInTheDocument();
  });

  it('conflicto de versión (409 CONCURRENT_UPDATE) avisa de edición simultánea', async () => {
    vi.mocked(editRoom).mockRejectedValue(
      new ApiRequestError(409, 'CONCURRENT_UPDATE', 'Conflicto concurrente'),
    );

    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    const rateInput = screen.getByLabelText(/Tarifa base por noche/);
    await userEvent.clear(rateInput);
    await userEvent.type(rateInput, '220000');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText(
        /Otro usuario actualizó la información de esta habitación mientras la editabas/,
      ),
    ).toBeInTheDocument();
  });

  it('si la habitación no está disponible al abrir, rechaza la edición', async () => {
    vi.mocked(getRoom).mockResolvedValue({
      ...INITIAL_ROOM,
      status: 'Occupied',
    });

    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    expect(await screen.findByText('No se puede editar la habitación 101')).toBeInTheDocument();
    expect(screen.getByText(/Solo se pueden editar habitaciones disponibles/)).toBeInTheDocument();
  });
});
