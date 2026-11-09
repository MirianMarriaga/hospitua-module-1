import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { registerRoom } from '@/api/roomService';
import { RegisterRoomPage } from '@/pages/administracion/register/RegisterRoomPage';
import { room } from '@/test/fixtures/rooms';
import { renderWithProviders } from '@/test/renderWithProviders';
import { chooseOption } from '@/test/chooseOption';

vi.mock('@/api/roomService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/roomService')>()),
  registerRoom: vi.fn(),
}));

async function fillForm(values: { number: string; floor: string; capacity: string; rate: string }) {
  await userEvent.type(screen.getByLabelText(/Número de habitación/), values.number);
  await userEvent.type(screen.getByLabelText(/Piso/), values.floor);
  await chooseOption(screen.getByLabelText(/Tipo/), 'Doble');
  await userEvent.type(screen.getByLabelText(/Capacidad máxima/), values.capacity);
  await userEvent.type(screen.getByLabelText(/Tarifa base/), values.rate);
  await userEvent.click(screen.getByRole('button', { name: 'Registrar habitación' }));
}

describe('RegisterRoomPage', () => {
  beforeEach(() => {
    vi.mocked(registerRoom).mockReset();
  });

  it('valida los campos con los textos del plan sin llamar al backend', async () => {
    renderWithProviders(<RegisterRoomPage />);
    await fillForm({ number: '12345678901', floor: '0', capacity: '1.5', rate: '100.123' });

    expect(
      await screen.findByText('El número de habitación debe tener máximo 10 caracteres.'),
    ).toBeInTheDocument();
    expect(screen.getByText('El piso debe ser un número entero mayor a 0.')).toBeInTheDocument();
    expect(
      screen.getByText('La capacidad máxima debe ser un número entero mayor a 0.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('La tarifa base debe ser mayor a 0 y tener máximo dos decimales.'),
    ).toBeInTheDocument();
    expect(registerRoom).not.toHaveBeenCalled();
  });

  it('marca el número si ya existe (409 ROOM_NUMBER_ALREADY_EXISTS)', async () => {
    vi.mocked(registerRoom).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_NUMBER_ALREADY_EXISTS', 'técnico'),
    );
    renderWithProviders(<RegisterRoomPage />);
    await fillForm({ number: '101a', floor: '1', capacity: '2', rate: '250000' });

    expect(
      await screen.findByText('Ya existe una habitación con el número 101A.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Número de habitación/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('técnico')).not.toBeInTheDocument();
  });

  it('envía los tipos del contrato y muestra la confirmación', async () => {
    vi.mocked(registerRoom).mockResolvedValue(
      room({
        roomNumber: '402',
        floor: 4,
        categoryRoom: 'DOBLE',
        maxCapacity: 2,
        baseRate: 250000.5,
      }),
    );
    renderWithProviders(<RegisterRoomPage />);
    await fillForm({ number: ' 402 ', floor: '4', capacity: '2', rate: '250000,50' });

    expect(await screen.findByText('Habitación 402 registrada')).toBeInTheDocument();
    // FR-012: la confirmación muestra el ID de la habitación.
    expect(screen.getByText('ID')).toBeInTheDocument();
    expect(screen.getByText('11111111-1111-4111-a111-111111111111')).toBeInTheDocument();
    expect(registerRoom).toHaveBeenCalledWith(
      { roomNumber: '402', floor: 4, categoryRoom: 'DOBLE', maxCapacity: 2, baseRate: 250000.5 },
      expect.anything(),
    );
    expect(screen.getByRole('link', { name: 'Volver al inventario' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Registrar otra' }));
    expect(screen.getByLabelText(/Número de habitación/)).toHaveValue('');
  });
});
