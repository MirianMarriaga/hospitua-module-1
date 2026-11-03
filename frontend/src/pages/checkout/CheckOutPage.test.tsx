import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { getActiveStay, registerCheckOut } from '@/api/checkOutService';
import { getSettlement } from '@/api/settlementService';
import { CheckOutPage } from '@/pages/checkout/CheckOutPage';
import {
  ACTIVE_STAY,
  SETTLEMENT_AVAILABLE,
  SETTLEMENT_UNAVAILABLE,
} from '@/test/fixtures/reception';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/checkOutService', () => ({ getActiveStay: vi.fn(), registerCheckOut: vi.fn() }));
vi.mock('@/api/settlementService', () => ({ getSettlement: vi.fn() }));

const ROUTE = '/check-out?reservationRef=RES-40001&roomId=room-411';

function renderCheckOut() {
  return renderWithProviders(<CheckOutPage />, { route: ROUTE, path: '/check-out' });
}

async function continueTo(title: string) {
  await userEvent.click(screen.getByRole('button', { name: 'Continuar' }));
  expect(await screen.findByText(title)).toBeInTheDocument();
}

describe('CheckOutPage', () => {
  beforeEach(() => {
    vi.mocked(getActiveStay).mockReset().mockResolvedValue(ACTIVE_STAY);
    vi.mocked(getSettlement).mockReset().mockResolvedValue(SETTLEMENT_AVAILABLE);
    vi.mocked(registerCheckOut).mockReset();
  });

  it('Paso 1: muestra la estadía activa con sus huéspedes', async () => {
    renderCheckOut();

    expect(await screen.findByText('RES-40001')).toBeInTheDocument();
    expect(getActiveStay).toHaveBeenCalledWith('RES-40001', 'room-411');
    expect(screen.getByText(/Pasaporte P1234567 · México/)).toBeInTheDocument();
    expect(screen.getByText('Ocupada')).toBeInTheDocument();
  });

  it('Paso 1: sin estadía activa no deja continuar', async () => {
    vi.mocked(getActiveStay).mockRejectedValue(
      new ApiRequestError(404, 'ACTIVE_STAY_NOT_FOUND', 'técnico'),
    );
    renderCheckOut();

    expect(
      await screen.findByText('No encontramos una estadía activa para esta habitación.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('Pasos 2 y 3 comparten una sola consulta de la liquidación', async () => {
    renderCheckOut();
    await screen.findByText('RES-40001');
    await continueTo('2. Liquidación de la estadía');
    expect(await screen.findByText('Información de la liquidación')).toBeInTheDocument();

    await continueTo('3. Pago');
    expect(screen.getByText('Resumen para el huésped')).toBeInTheDocument();
    expect(getSettlement).toHaveBeenCalledTimes(1);
    expect(getSettlement).toHaveBeenCalledWith('stay-1');

    await userEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(await screen.findByText('2. Liquidación de la estadía')).toBeInTheDocument();
  });

  it('con la liquidación UNAVAILABLE deja seguir hasta liberar la habitación', async () => {
    vi.mocked(getSettlement).mockResolvedValue(SETTLEMENT_UNAVAILABLE);
    renderCheckOut();
    await screen.findByText('RES-40001');
    await continueTo('2. Liquidación de la estadía');

    expect(
      await screen.findByText('La liquidación no está disponible en este momento.'),
    ).toBeInTheDocument();
    await continueTo('3. Pago');
    await continueTo('4. Confirmación y cierre');
  });

  it('Paso 4: el botón se habilita solo con la casilla marcada', async () => {
    renderCheckOut();
    await screen.findByText('RES-40001');
    await continueTo('2. Liquidación de la estadía');
    await screen.findByText('Información de la liquidación');
    await continueTo('3. Pago');
    await continueTo('4. Confirmación y cierre');

    const confirm = screen.getByRole('button', { name: 'Confirmar Check-Out' });
    expect(confirm).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox'));
    expect(confirm).toBeEnabled();
    await userEvent.click(screen.getByRole('checkbox'));
    expect(confirm).toBeDisabled();
  });

  it('Paso 5: registra la salida y muestra "Habitación: Pendiente de limpieza"', async () => {
    vi.mocked(registerCheckOut).mockResolvedValue({
      stayId: 'stay-1',
      roomId: 'room-411',
      roomNumber: '411',
      roomStatus: 'PendingCleaning',
      checkOutDate: '2026-10-31',
    });
    renderCheckOut();
    await screen.findByText('RES-40001');
    await continueTo('2. Liquidación de la estadía');
    await screen.findByText('Información de la liquidación');
    await continueTo('3. Pago');
    await continueTo('4. Confirmación y cierre');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Check-Out' }));

    expect(await screen.findByText('Check-out completado')).toBeInTheDocument();
    expect(registerCheckOut).toHaveBeenCalledWith({ stayId: 'stay-1', confirmed: true });
    expect(screen.getByText(/Habitación:/).parentElement).toHaveTextContent(
      'Habitación: Pendiente de limpieza',
    );
  });

  it('Paso 4: muestra el rechazo del backend con el estado actual', async () => {
    vi.mocked(registerCheckOut).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'técnico', {
        currentStatus: 'PendingCleaning',
      }),
    );
    renderCheckOut();
    await screen.findByText('RES-40001');
    await continueTo('2. Liquidación de la estadía');
    await screen.findByText('Información de la liquidación');
    await continueTo('3. Pago');
    await continueTo('4. Confirmación y cierre');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Check-Out' }));

    expect(
      await screen.findByText(
        'La habitación está Pendiente de limpieza; solo se puede hacer el check-out de una habitación Ocupada.',
      ),
    ).toBeInTheDocument();
  });
});
