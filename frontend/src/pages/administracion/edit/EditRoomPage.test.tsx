import { screen, waitFor } from '@testing-library/react';
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

  it('la pantalla de éxito se queda aunque la habitación vuelva con otra versión', async () => {
    const updated: Room = { ...INITIAL_ROOM, baseRate: 210000, version: 4 };
    vi.mocked(editRoom).mockImplementation(() => {
      // El backend ya guardó: la consulta que sigue al éxito trae la versión nueva.
      vi.mocked(getRoom).mockResolvedValue(updated);
      return Promise.resolve({
        changed: true,
        changes: [{ field: 'baseRate', previousValue: 180000, newValue: 210000 }],
        room: updated,
      });
    });

    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });
    await screen.findByText('Editar habitación 101');
    const rateInput = screen.getByLabelText(/Tarifa base por noche/);
    await userEvent.clear(rateInput);
    await userEvent.type(rateInput, '210000');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Habitación 101 actualizada')).toBeInTheDocument();
    await waitFor(() => expect(getRoom).toHaveBeenCalledTimes(2));
    expect(screen.getByText('Habitación 101 actualizada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
  });

  it('"Cargar los datos actuales" reinicia el formulario con la versión nueva', async () => {
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
    await screen.findByText(/Otro usuario actualizó la información/);

    vi.mocked(getRoom).mockResolvedValue({ ...INITIAL_ROOM, baseRate: 250000, version: 4 });
    await userEvent.click(screen.getByRole('button', { name: 'Cargar los datos actuales' }));

    await waitFor(() => expect(screen.getByLabelText(/Tarifa base por noche/)).toHaveValue(250000));
    expect(screen.queryByText(/Otro usuario actualizó la información/)).not.toBeInTheDocument();
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
  it('llega precargado con los datos actuales y no muestra ni deja editar el UUID', async () => {
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    expect(screen.getByLabelText(/Número de habitación/)).toHaveValue('101');
    expect(screen.getByLabelText(/Piso/)).toHaveValue(1);
    expect(screen.getByLabelText(/Tipo/)).toHaveTextContent('Sencilla');
    expect(screen.getByLabelText(/Capacidad máxima/)).toHaveValue(1);
    expect(screen.getByLabelText(/Tarifa base por noche/)).toHaveValue(180000);
    expect(screen.queryByText('room-101')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('room-101')).not.toBeInTheDocument();
  });

  it('valida los campos con los textos del plan sin llamar al backend', async () => {
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    await userEvent.clear(screen.getByLabelText(/Número de habitación/));
    await userEvent.clear(screen.getByLabelText(/Capacidad máxima/));
    await userEvent.type(screen.getByLabelText(/Capacidad máxima/), '0');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('El número de habitación es obligatorio.')).toBeInTheDocument();
    expect(
      screen.getByText('La capacidad máxima debe ser un número entero mayor a 0.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Número de habitación/)).toHaveFocus();
    expect(editRoom).not.toHaveBeenCalled();
  });

  it('un VALIDATION_ERROR del backend marca su campo y conserva los demás valores', async () => {
    vi.mocked(editRoom).mockRejectedValue(
      new ApiRequestError(400, 'VALIDATION_ERROR', 'mensaje técnico', { field: 'baseRate' }),
    );
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    await userEvent.clear(screen.getByLabelText(/Piso/));
    await userEvent.type(screen.getByLabelText(/Piso/), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText('La tarifa base debe ser mayor a 0 y tener máximo dos decimales.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Piso/)).toHaveValue(2);
    expect(screen.queryByText('mensaje técnico')).not.toBeInTheDocument();
  });

  it('un 409 ROOM_INVALID_STATE al guardar muestra el estado actual en español', async () => {
    vi.mocked(editRoom).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'mensaje técnico', {
        currentStatus: 'PendingCleaning',
      }),
    );
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    await userEvent.clear(screen.getByLabelText(/Piso/));
    await userEvent.type(screen.getByLabelText(/Piso/), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText(
        'La habitación no está disponible para edición (estado actual: Pendiente de limpieza).',
      ),
    ).toBeInTheDocument();
  });

  it('un 409 ROOM_NUMBER_ALREADY_EXISTS marca el número con el mismo texto que al registrar', async () => {
    vi.mocked(editRoom).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_NUMBER_ALREADY_EXISTS', 'mensaje técnico'),
    );
    renderWithProviders(<EditRoomPage />, {
      route: '/administracion/habitaciones/room-101/editar',
      path: '/administracion/habitaciones/:roomId/editar',
    });

    await screen.findByText('Editar habitación 101');
    const numberInput = screen.getByLabelText(/Número de habitación/);
    await userEvent.clear(numberInput);
    await userEvent.type(numberInput, '204a');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText('Ya existe una habitación con el número 204A.'),
    ).toBeInTheDocument();
    expect(numberInput).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText(/aunque esté inactiva/)).not.toBeInTheDocument();
  });
});
