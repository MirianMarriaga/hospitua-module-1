import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import {
  getCleaningPanel,
  startCleaning,
  type CleaningPanel,
  type CleaningRoom,
} from '@/api/cleaningService';
import { reportDamage } from '@/api/damageReportService';
import { CleaningPanelPage } from '@/pages/limpieza/CleaningPanelPage';
import { useSessionStore } from '@/stores/sessionStore';
import { LocationProbe, renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/cleaningService', () => ({ getCleaningPanel: vi.fn(), startCleaning: vi.fn() }));
vi.mock('@/api/damageReportService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/damageReportService')>()),
  reportDamage: vi.fn(),
}));

const PENDING: CleaningRoom = {
  roomId: 'r101',
  roomNumber: '101',
  categoryRoom: 'DOBLE',
  status: 'PendingCleaning',
  lastCleaningDateTime: '2026-10-07T16:40:00-05:00',
};
const AVAILABLE: CleaningRoom = {
  roomId: 'r102',
  roomNumber: '102',
  categoryRoom: 'SENCILLA',
  status: 'Available',
};
const PANEL: CleaningPanel = { rooms: [PENDING, AVAILABLE] };

function renderPanel() {
  return renderWithProviders(<CleaningPanelPage />, {
    route: '/limpieza',
    path: '/limpieza',
    extraRoutes: [
      {
        path: '/limpieza/tarea',
        element: (
          <>
            <LocationProbe label="tarea" />
            <Link to="/limpieza">Volver al panel</Link>
          </>
        ),
      },
    ],
  });
}

function rowOf(roomNumber: string) {
  return screen.getByText(roomNumber, { selector: 'div' }).closest('tr')!;
}

describe('CleaningPanelPage', () => {
  beforeEach(() => {
    vi.mocked(getCleaningPanel).mockReset().mockResolvedValue(PANEL);
    vi.mocked(startCleaning).mockReset();
    useSessionStore.setState({
      session: { username: 'ltorres', fullName: 'Luis Torres', area: 'LIMPIEZA' },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('lista las habitaciones con tipo, estado, última limpieza y el conteo por estado', async () => {
    renderPanel();

    expect(await screen.findByRole('heading', { name: 'Bienvenido, Luis Torres' })).toBeVisible();
    await screen.findByText('101', { selector: 'div' });
    const row101 = rowOf('101');
    expect(within(row101).getByText('Doble')).toBeInTheDocument();
    expect(within(row101).getByText('Pendiente de limpieza')).toBeInTheDocument();
    expect(within(row101).getByText('07-10-2026 16:40')).toBeInTheDocument();
    const row102 = rowOf('102');
    expect(within(row102).getByText('Disponible')).toBeInTheDocument();
    expect(within(row102).getByText('Sin registro')).toBeInTheDocument();

    const summary = screen.getByRole('region', { name: 'Resumen de habitaciones' });
    expect(within(summary).getByText('Pendientes de limpieza').previousSibling).toHaveTextContent(
      '1',
    );
    expect(within(summary).getByText('Disponibles').previousSibling).toHaveTextContent('1');
  });

  it('ofrece "Iniciar limpieza" en las dos y "Reportar daño" solo en las disponibles', async () => {
    renderPanel();
    await screen.findByText('101', { selector: 'div' });

    expect(within(rowOf('101')).queryByRole('button', { name: /Reportar daño/ })).toBeNull();
    expect(within(rowOf('102')).getByRole('button', { name: /Reportar daño/ })).toBeInTheDocument();

    expect(
      within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }),
    ).toBeInTheDocument();
    expect(
      within(rowOf('102')).getByRole('button', { name: /Iniciar limpieza/ }),
    ).toBeInTheDocument();
  });

  it('busca por número exacto, avisa si no lo encuentra y al vaciar vuelve el listado', async () => {
    renderPanel();
    await screen.findByText('101', { selector: 'div' });
    const search = screen.getByRole('searchbox', { name: 'Buscar habitación por número' });

    await userEvent.type(search, '10');
    expect(screen.getByText('No se encontró la habitación 10')).toBeInTheDocument();

    await userEvent.type(search, '1');
    expect(screen.getByText('101', { selector: 'div' })).toBeInTheDocument();
    expect(screen.queryByText('102', { selector: 'div' })).not.toBeInTheDocument();

    await userEvent.clear(search);
    expect(screen.getByText('102', { selector: 'div' })).toBeInTheDocument();
  });

  it('avisa cuando no hay habitaciones para listar', async () => {
    vi.mocked(getCleaningPanel).mockResolvedValue({ rooms: [] });
    renderPanel();

    expect(
      await screen.findByText('No hay habitaciones pendientes de limpieza ni disponibles'),
    ).toBeInTheDocument();
  });

  it('con una tarea activa lleva a su vista en lugar de mostrar el panel', async () => {
    vi.mocked(getCleaningPanel).mockResolvedValue({
      activeTask: { taskId: 't1', startDateTime: '2026-10-08T09:15:00-05:00', room: PENDING },
      rooms: [],
    });
    renderPanel();

    expect(await screen.findByTestId('location')).toHaveTextContent('tarea');
  });

  it('vuelve a consultar el panel cada 60 segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPanel();
    await screen.findByText('101', { selector: 'div' });
    expect(getCleaningPanel).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(60_000);

    await waitFor(() => expect(getCleaningPanel).toHaveBeenCalledTimes(2));
  });

  it('confirma, inicia la limpieza y lleva a la tarea activa', async () => {
    let resolve: (value: { taskId: string }) => void = () => {};
    vi.mocked(startCleaning).mockReturnValue(new Promise((done) => (resolve = done)));
    renderPanel();
    await screen.findByText('101', { selector: 'div' });

    await userEvent.click(within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }));
    const dialog = screen.getByRole('alertdialog', { name: 'Iniciar limpieza' });
    expect(dialog).toHaveTextContent(
      'La habitación 101 quedará a tu cargo hasta que termines la limpieza o la dejes para otro compañero.',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Iniciar limpieza' }));

    expect(within(dialog).getByRole('button', { name: 'Iniciando…' })).toBeDisabled();
    expect(startCleaning).toHaveBeenCalledWith('r101', expect.anything());
    resolve({ taskId: 't1' });

    expect(await screen.findByTestId('location')).toHaveTextContent('tarea');
  });

  it('cancelar no inicia nada', async () => {
    renderPanel();
    await screen.findByText('101', { selector: 'div' });

    await userEvent.click(within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(startCleaning).not.toHaveBeenCalled();
  });

  it('si otro compañero la tomó, dice el estado actual y recarga el panel', async () => {
    vi.mocked(startCleaning).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'técnico', { currentStatus: 'InCleaning' }),
    );
    renderPanel();
    await screen.findByText('101', { selector: 'div' });
    vi.mocked(getCleaningPanel).mockResolvedValue({ rooms: [AVAILABLE] });

    await userEvent.click(within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }));
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Iniciar limpieza' }),
    );

    expect(
      await screen.findByText('La habitación está En limpieza; ahora no se puede limpiar.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('técnico')).not.toBeInTheDocument();
    await waitFor(() => expect(getCleaningPanel).toHaveBeenCalledTimes(2));

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByText('101', { selector: 'div' })).not.toBeInTheDocument();
  });

  it('muestra el texto de cada rechazo según su errorCode', async () => {
    vi.mocked(startCleaning).mockRejectedValue(
      new ApiRequestError(409, 'ACTIVE_TASK_EXISTS', 'técnico'),
    );
    renderPanel();
    await screen.findByText('101', { selector: 'div' });

    await userEvent.click(within(rowOf('102')).getByRole('button', { name: /Iniciar limpieza/ }));
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Iniciar limpieza' }),
    );

    expect(
      await screen.findByText(
        'Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra.',
      ),
    ).toBeInTheDocument();
  });

  it('ante un fallo de red sugiere intentar de nuevo', async () => {
    vi.mocked(startCleaning).mockRejectedValue(
      new ApiRequestError(0, NETWORK_ERROR, 'No hay conexión con el servidor.'),
    );
    renderPanel();
    await screen.findByText('101', { selector: 'div' });

    await userEvent.click(within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }));
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Iniciar limpieza' }),
    );

    expect(
      await screen.findByText('No hay conexión con el servidor. Revisa tu red e intenta de nuevo.'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Iniciar limpieza' }),
    ).toBeEnabled();
  });

  it('vuelve a consultar el panel al volver de una acción', async () => {
    vi.mocked(startCleaning).mockResolvedValue({ taskId: 't1' });
    renderPanel();
    await screen.findByText('101', { selector: 'div' });
    expect(getCleaningPanel).toHaveBeenCalledTimes(1);

    await userEvent.click(within(rowOf('101')).getByRole('button', { name: /Iniciar limpieza/ }));
    await userEvent.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Iniciar limpieza' }),
    );
    await screen.findByTestId('location');
    const calls = vi.mocked(getCleaningPanel).mock.calls.length;

    await userEvent.click(screen.getByRole('link', { name: 'Volver al panel' }));

    await waitFor(() => expect(getCleaningPanel).toHaveBeenCalledTimes(calls + 1));
  });

  it('reporta un daño, avisa arriba del panel y lo vuelve a consultar', async () => {
    vi.mocked(reportDamage).mockResolvedValue({ damageReportId: 'd1' });
    renderPanel();
    await screen.findByText('101', { selector: 'div' });
    vi.mocked(getCleaningPanel).mockResolvedValue({ rooms: [PENDING] });

    await userEvent.click(within(rowOf('102')).getByRole('button', { name: /Reportar daño/ }));
    const dialog = screen.getByRole('alertdialog', { name: 'Reportar daño' });
    expect(dialog).toHaveTextContent(
      'La habitación 102 quedará fuera de servicio hasta que mantenimiento la repare.',
    );
    await userEvent.type(within(dialog).getByLabelText('Descripción del daño *'), 'Ventana rota');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reportar daño' }));

    expect(
      await screen.findByText(
        'Daño reportado. La habitación 102 quedó fuera de servicio para que mantenimiento la repare.',
      ),
    ).toBeInTheDocument();
    expect(reportDamage).toHaveBeenCalledWith('r102', 'Ventana rota');
    await waitFor(() =>
      expect(screen.queryByText('102', { selector: 'div' })).not.toBeInTheDocument(),
    );
  });

  it('al volver de la tarea activa muestra el aviso que trae la navegación', async () => {
    renderWithProviders(<CleaningPanelPage />, {
      route: {
        pathname: '/limpieza',
        state: { notice: 'Limpieza de la habitación 101 finalizada.' },
      },
      path: '/limpieza',
    });

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Limpieza de la habitación 101 finalizada.',
    );
  });
});
