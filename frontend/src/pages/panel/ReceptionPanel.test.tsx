import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getArrivals, getDepartures, getReceptionKpis } from '@/api/receptionService';
import { ReceptionPanel } from '@/pages/panel/ReceptionPanel';
import { useSessionStore } from '@/stores/sessionStore';
import { ARRIVALS, DEPARTURES } from '@/test/fixtures/reception';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/receptionService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/receptionService')>()),
  getArrivals: vi.fn(),
  getDepartures: vi.fn(),
  getReceptionKpis: vi.fn(),
}));

describe('ReceptionPanel', () => {
  beforeEach(() => {
    useSessionStore.setState({ session: null, expired: false });
    vi.mocked(getArrivals).mockReset().mockResolvedValue(ARRIVALS);
    vi.mocked(getDepartures).mockReset().mockResolvedValue(DEPARTURES);
    vi.mocked(getReceptionKpis).mockReset().mockResolvedValue({
      departuresToday: 4,
      overdueDepartures: 1,
      occupiedRooms: 12,
    });
  });

  it('muestra "—" en Llegadas de hoy cuando el backend no envía arrivalsToday', async () => {
    renderWithProviders(<ReceptionPanel />);

    const kpis = screen.getByRole('region', { name: 'Indicadores del día' });
    expect(await within(kpis).findByText('12')).toBeInTheDocument();
    expect(within(kpis).getByText('—')).toBeInTheDocument();
    expect(within(kpis).getByText('4')).toBeInTheDocument();
  });

  it('agrupa las llegadas por reserva y muestra los avisos de estado', async () => {
    renderWithProviders(<ReceptionPanel />);

    const tags = await screen.findAllByText('RES-10482');
    expect(tags.length).toBe(2);
    expect(screen.getAllByText('1 de 2 pendientes').length).toBe(2);
    expect(screen.getByText('No disponible: Bloqueo técnico')).toBeInTheDocument();

    const ready = screen.getByRole('cell', { name: /201/ }).closest('tr')!;
    expect(within(ready).getByRole('link', { name: 'Check-in' })).toHaveAttribute(
      'href',
      '/check-in?reservationRef=RES-10482&roomId=room-201',
    );
    const blocked = screen.getByRole('cell', { name: /202/ }).closest('tr')!;
    expect(within(blocked).getByRole('button', { name: 'Check-in' })).toBeDisabled();
  });

  it('avisa si la lista del día no está disponible', async () => {
    vi.mocked(getArrivals).mockResolvedValue({ listAvailable: false, groups: [] });
    renderWithProviders(<ReceptionPanel />);

    expect(
      await screen.findByText(
        'Lista de llegadas no disponible. Esperando sincronización de Módulo 2.',
      ),
    ).toBeInTheDocument();
  });

  it('muestra las salidas con "Vencida" y el enlace al check-out', async () => {
    renderWithProviders(<ReceptionPanel />, { route: '/?tab=salidas' });

    const row = (await screen.findByRole('cell', { name: '411' })).closest('tr')!;
    expect(within(row).getByText('Vencida')).toBeInTheDocument();
    expect(within(row).getByText('30-10-2026')).toHaveClass('text-due');
    expect(within(row).getByRole('link', { name: 'Check-out' })).toHaveAttribute(
      'href',
      '/check-out?reservationRef=RES-40001&roomId=room-411',
    );
  });

  it('aplica la búsqueda al dejar de escribir y la comparte entre pestañas', async () => {
    renderWithProviders(<ReceptionPanel />);
    await screen.findAllByText('RES-10482');

    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar reservas' }), 'gonz');
    await waitFor(() => expect(getArrivals).toHaveBeenLastCalledWith('gonz'));
    expect(getArrivals).not.toHaveBeenCalledWith('g');

    await userEvent.click(screen.getByRole('tab', { name: 'Salidas' }));
    await waitFor(() => expect(getDepartures).toHaveBeenLastCalledWith('gonz'));
  });

  it('sin coincidencias muestra el mensaje vacío del prototipo', async () => {
    vi.mocked(getArrivals).mockResolvedValue({ listAvailable: true, groups: [] });
    renderWithProviders(<ReceptionPanel />);

    expect(await screen.findByText('No hay reservas de llegada que mostrar.')).toBeInTheDocument();
  });

  it('el hero muestra el nombre de la sesión y cada pestaña tiene las columnas esperadas', async () => {
    useSessionStore.setState({
      session: { username: 'ana', fullName: 'Ana Ramírez', area: 'RECEPCION' },
    });
    renderWithProviders(<ReceptionPanel />);

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Bienvenida, Ana Ramírez' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Estancias · llegadas y salidas del día')).toBeInTheDocument();

    await screen.findByText('Reserva / Grupo');
    const arrivalHeaders = screen.getAllByRole('columnheader').map((th) => th.textContent?.trim());
    expect(arrivalHeaders).toEqual([
      'Reserva / Grupo',
      'Hab.',
      'Huésped titular',
      'Estadía',
      'Personas',
      'Tipo habitación',
      'Fuente',
      'Acciones',
    ]);

    await userEvent.click(screen.getByRole('tab', { name: 'Salidas' }));

    await screen.findByText('Fecha entrada');
    const departureHeaders = screen
      .getAllByRole('columnheader')
      .map((th) => th.textContent?.trim());
    expect(departureHeaders).toEqual([
      'Hab.',
      'Huésped titular',
      'Fecha entrada',
      'Fecha salida',
      'Personas',
      'Tipo habitación',
      'Fuente',
      'Acciones',
    ]);
  });
});
