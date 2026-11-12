import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { getRoomStateHistory, type RoomStatePeriod } from '@/api/historyService';
import { RoomStateHistoryPage } from '@/pages/gerencia/RoomStateHistoryPage';
import { page } from '@/test/fixtures/rooms';
import { renderWithProviders } from '@/test/renderWithProviders';
import { chooseOption } from '@/test/chooseOption';

vi.mock('@/api/historyService', () => ({ getRoomStateHistory: vi.fn() }));

const PERIODS: RoomStatePeriod[] = [
  {
    roomId: 'r1',
    roomNumber: '305',
    categoryRoom: 'SUITE',
    status: 'TechnicalBlock',
    startDateTime: '2026-10-30T08:15:00-05:00',
    sourceFlow: 'TECHNICAL_BLOCK',
    sourceFlowName: 'Programar bloqueo técnico para habitación',
  },
  {
    roomId: 'r1',
    roomNumber: '305',
    categoryRoom: 'SUITE',
    status: 'Available',
    startDateTime: '2026-10-29T10:00:00-05:00',
    endDateTime: '2026-10-30T08:15:00-05:00',
    actorName: 'Laura Méndez',
    sourceFlow: 'REGISTER_ROOM',
    sourceFlowName: 'Registrar habitación',
  },
];

describe('RoomStateHistoryPage', () => {
  beforeEach(() => {
    vi.mocked(getRoomStateHistory).mockReset();
    vi.mocked(getRoomStateHistory).mockResolvedValue(page(PERIODS));
  });

  it('muestra "En curso" y "Sistema", con las horas en DD-MM-YYYY HH:MM, sin el flujo de origen', async () => {
    renderWithProviders(<RoomStateHistoryPage />);

    const open = (await screen.findByText('En curso')).closest('tr')!;
    expect(within(open).getByText('Sistema')).toBeInTheDocument();
    expect(within(open).getByText('Bloqueo técnico')).toBeInTheDocument();
    expect(within(open).getByText('30-10-2026 08:15')).toBeInTheDocument();
    expect(screen.getByText('Laura Méndez')).toBeInTheDocument();
    expect(screen.queryByText('Flujo de origen')).not.toBeInTheDocument();
    expect(screen.queryByText('Registrar habitación')).not.toBeInTheDocument();
  });

  it('usa el roomId que llega desde "Ver historial" y lo quita al escribir otro número', async () => {
    renderWithProviders(<RoomStateHistoryPage />, { route: '/?roomId=r1&roomNumber=305' });
    await screen.findByText('En curso');
    expect(getRoomStateHistory).toHaveBeenCalledWith({ roomId: 'r1', roomNumber: '305' }, 1);
    expect(screen.getByLabelText('Habitación')).toHaveValue('305');

    await userEvent.clear(screen.getByLabelText('Habitación'));
    await userEvent.type(screen.getByLabelText('Habitación'), '101');

    // Escribir y esperar los SEARCH_DEBOUNCE_MS del buscador pasa del segundo por defecto de
    // waitFor con la máquina cargada; por eso fallaba de forma intermitente.
    await waitFor(
      () => expect(getRoomStateHistory).toHaveBeenLastCalledWith({ roomNumber: '101' }, 1),
      { timeout: 3000 },
    );
  });

  it('no consulta con "Desde" posterior a "Hasta" y lo explica junto a las fechas', async () => {
    renderWithProviders(<RoomStateHistoryPage />, { route: '/?from=2026-10-31&to=2026-10-01' });

    expect(
      await screen.findByText('La fecha "Desde" no puede ser posterior a "Hasta".'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Desde')).toHaveAttribute('aria-invalid', 'true');
    expect(getRoomStateHistory).not.toHaveBeenCalled();
  });

  it('muestra el mismo texto si el backend responde INVALID_DATE_RANGE', async () => {
    vi.mocked(getRoomStateHistory).mockRejectedValue(
      new ApiRequestError(400, 'INVALID_DATE_RANGE', 'técnico'),
    );
    renderWithProviders(<RoomStateHistoryPage />, { route: '/?from=2026-10-01' });

    expect(
      await screen.findByText('La fecha "Desde" no puede ser posterior a "Hasta".'),
    ).toBeInTheDocument();
  });

  it('sin coincidencias muestra "No se encontraron resultados"', async () => {
    vi.mocked(getRoomStateHistory).mockResolvedValue(page([]));
    renderWithProviders(<RoomStateHistoryPage />, { route: '/?status=Inactive' });

    expect(await screen.findByText('No se encontraron resultados')).toBeInTheDocument();
  });
  it('al cambiar un filtro vuelve a la página 1', async () => {
    renderWithProviders(<RoomStateHistoryPage />, { route: '/?page=3' });
    await screen.findByText('En curso');
    expect(getRoomStateHistory).toHaveBeenLastCalledWith({}, 3);

    await chooseOption(screen.getByLabelText('Tipo'), 'Doble');

    await waitFor(() =>
      expect(getRoomStateHistory).toHaveBeenLastCalledWith(
        expect.objectContaining({ categoryRoom: 'DOBLE' }),
        1,
      ),
    );
  });
});
