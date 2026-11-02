import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { registerCheckIn, validateCheckIn } from '@/api/checkInService';
import { CheckInPage } from '@/pages/checkin/CheckInPage';
import { VALIDATION } from '@/test/fixtures/reception';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/checkInService', () => ({ validateCheckIn: vi.fn(), registerCheckIn: vi.fn() }));

const ROUTE = '/check-in?reservationRef=RES-12345&roomId=room-304';

function guestBlock(number: number) {
  return screen.getByRole('group', { name: `Huésped ${number}` });
}

function setDate(block: HTMLElement, value: string) {
  fireEvent.change(within(block).getByLabelText('Fecha de nacimiento'), { target: { value } });
}

async function goToGuests() {
  renderWithProviders(<CheckInPage />, { route: ROUTE, path: '/check-in' });
  await screen.findByText('María González');
  await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
  await screen.findByText(/2\. Datos de huéspedes/);
}

async function fillSecondGuest(nationality = 'Colombia') {
  const second = guestBlock(2);
  await userEvent.type(within(second).getByLabelText('Nombres'), 'Carlos');
  await userEvent.type(within(second).getByLabelText('Apellidos'), 'Pérez');
  await userEvent.type(within(second).getByLabelText('Número de documento'), '79888777');
  const country = within(second).getByLabelText('Nacionalidad');
  await userEvent.selectOptions(country, nationality);
  setDate(second, '1988-02-14');
}

describe('CheckInPage', () => {
  beforeEach(() => {
    vi.mocked(validateCheckIn).mockReset().mockResolvedValue(VALIDATION);
    vi.mocked(registerCheckIn).mockReset();
  });

  it('pide elegir la reserva desde el panel si faltan los parámetros', () => {
    renderWithProviders(<CheckInPage />, { route: '/check-in', path: '/check-in' });
    expect(screen.getByText('Elige la reserva desde el panel de recepción.')).toBeInTheDocument();
    expect(validateCheckIn).not.toHaveBeenCalled();
  });

  it('Paso 1: valida la reserva y muestra el titular en solo lectura', async () => {
    renderWithProviders(<CheckInPage />, { route: ROUTE, path: '/check-in' });

    expect(await screen.findByText('María González')).toBeInTheDocument();
    expect(validateCheckIn).toHaveBeenCalledWith('RES-12345', 'room-304');
    expect(screen.getByText('Directa')).toBeInTheDocument();
    expect(screen.getByText(/31-10-2026 – 03-11-2026/)).toBeInTheDocument();
  });

  it('Paso 1: explica un rechazo con el estado actual de la habitación', async () => {
    vi.mocked(validateCheckIn).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'técnico', { currentStatus: 'InCleaning' }),
    );
    renderWithProviders(<CheckInPage />, { route: ROUTE, path: '/check-in' });

    expect(
      await screen.findByText(
        'La habitación está En limpieza; solo se puede hacer el check-in de una habitación Reservada para esta reserva.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('Paso 2: un bloque por ocupante, con el titular precargado y bloqueado', async () => {
    await goToGuests();

    const first = guestBlock(1);
    expect(within(first).getByLabelText('Nombres')).toHaveValue('María');
    expect(within(first).getByLabelText('Nombres')).toHaveAttribute('readonly');
    expect(within(first).getByText('Titular de la reserva')).toBeInTheDocument();
    expect(within(guestBlock(2)).getByLabelText('Nombres')).toHaveValue('');
    expect(screen.queryByRole('group', { name: 'Huésped 3' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(await screen.findByText('1. Información de la reserva')).toBeInTheDocument();
  });

  it('Paso 2: valida la fecha de nacimiento y los datos migratorios de extranjeros', async () => {
    await goToGuests();
    setDate(guestBlock(1), '2999-01-01');
    await fillSecondGuest('Argentina');
    expect(
      within(guestBlock(2)).getByText('Datos de control migratorio'),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(
      await within(guestBlock(1)).findByText(
        'La fecha de nacimiento es obligatoria y debe ser anterior a hoy.',
      ),
    ).toBeInTheDocument();
    expect(
      within(guestBlock(2)).getByText(
        'El lugar de procedencia es obligatorio para huéspedes extranjeros.',
      ),
    ).toBeInTheDocument();
    expect(
      within(guestBlock(2)).getByText(
        'El lugar de destino es obligatorio para huéspedes extranjeros.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('3. Resumen y confirmación')).not.toBeInTheDocument();
  });

  it('Paso 2: rechaza dos huéspedes con el mismo documento', async () => {
    await goToGuests();
    setDate(guestBlock(1), '1990-05-10');
    await fillSecondGuest();
    const number = within(guestBlock(2)).getByLabelText('Número de documento');
    await userEvent.clear(number);
    await userEvent.type(number, '1020304050');

    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(
      await screen.findByText('Otro huésped de esta habitación ya tiene este documento.'),
    ).toBeInTheDocument();
  });

  it('Pasos 3 y 4: confirma, envía el contrato y muestra el éxito sin jerga técnica', async () => {
    vi.mocked(registerCheckIn).mockResolvedValue({
      stayId: 'stay-9',
      reservationRef: 'RES-12345',
      roomId: 'room-304',
      roomNumber: '304',
      roomStatus: 'Occupied',
      checkInDate: '2026-10-31',
    });
    await goToGuests();
    setDate(guestBlock(1), '1990-05-10');
    await fillSecondGuest();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('3. Resumen y confirmación')).toBeInTheDocument();
    expect(screen.getByText(/Carlos Pérez/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Check-In' }));

    expect(await screen.findByText('Check-in completado')).toBeInTheDocument();
    expect(registerCheckIn).toHaveBeenCalledWith({
      reservationRef: 'RES-12345',
      roomId: 'room-304',
      guests: [
        {
          firstName: 'María',
          lastName: 'González',
          documentType: 'CC',
          documentNumber: '1020304050',
          nationality: 'Colombia',
          birthDate: '1990-05-10',
          originPlace: undefined,
          destinationPlace: undefined,
          isReservationGuest: true,
        },
        {
          firstName: 'Carlos',
          lastName: 'Pérez',
          documentType: 'CC',
          documentNumber: '79888777',
          nationality: 'Colombia',
          birthDate: '1988-02-14',
          originPlace: undefined,
          destinationPlace: undefined,
          isReservationGuest: false,
        },
      ],
    });
    expect(document.body).not.toHaveTextContent(/Módulo|cola|síncron/i);
  });

  it('Paso 3: un 400 de un huésped vuelve al Paso 2 y marca el campo', async () => {
    vi.mocked(registerCheckIn).mockRejectedValue(
      new ApiRequestError(400, 'VALIDATION_ERROR', 'técnico', {
        guestIndex: 1,
        field: 'birthDate',
      }),
    );
    await goToGuests();
    setDate(guestBlock(1), '1990-05-10');
    await fillSecondGuest();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Check-In' }));

    expect(await screen.findByText('Revisa los datos del huésped 2.')).toBeInTheDocument();
    expect(
      within(guestBlock(2)).getByText(
        'La fecha de nacimiento es obligatoria y debe ser anterior a hoy.',
      ),
    ).toBeInTheDocument();
    expect(within(guestBlock(2)).getByLabelText('Nombres')).toHaveValue('Carlos');
  });

  it('Paso 3: los demás rechazos se quedan en el resumen', async () => {
    vi.mocked(registerCheckIn).mockRejectedValue(
      new ApiRequestError(409, 'STAY_ALREADY_EXISTS', 'técnico'),
    );
    await goToGuests();
    setDate(guestBlock(1), '1990-05-10');
    await fillSecondGuest();
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar Check-In' }));

    expect(
      await screen.findByText('Esta habitación ya tiene el check-in registrado.'),
    ).toBeInTheDocument();
    expect(screen.getByText('3. Resumen y confirmación')).toBeInTheDocument();
  });
});
