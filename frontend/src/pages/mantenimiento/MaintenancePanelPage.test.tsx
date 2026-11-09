import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { getLatestDamageReport, reportDamage } from '@/api/damageReportService';
import { getCurrentTechnicalBlock, scheduleTechnicalBlock } from '@/api/technicalBlockService';
import {
  getMaintenancePanel,
  startRepair,
  type MaintenancePanel,
  type MaintenanceRoom,
} from '@/api/maintenanceService';
import { MaintenancePanelPage } from '@/pages/mantenimiento/MaintenancePanelPage';
import { useSessionStore } from '@/stores/sessionStore';
import { LocationProbe, renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/maintenanceService', () => ({
  getMaintenancePanel: vi.fn(),
  startRepair: vi.fn(),
}));
vi.mock('@/api/technicalBlockService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/technicalBlockService')>()),
  scheduleTechnicalBlock: vi.fn(),
  getCurrentTechnicalBlock: vi.fn(),
}));
// "Hoy" fijo en Bogotá para las reglas de fechas de "Programar mantenimiento".
vi.mock('@/lib/format', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/format')>()),
  todayInBogota: () => '2026-11-09',
}));
vi.mock('@/api/damageReportService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/damageReportService')>()),
  reportDamage: vi.fn(),
  getLatestDamageReport: vi.fn(),
}));

function room(
  roomNumber: string,
  status: MaintenanceRoom['status'],
  extra: Partial<MaintenanceRoom> = {},
): MaintenanceRoom {
  return {
    roomId: `r${roomNumber}`,
    roomNumber,
    categoryRoom: 'DOBLE',
    status,
    overdue: false,
    hasOpenTask: false,
    ...extra,
  };
}

// En el orden del backend: vencidos, resto de intervención y demás (FR-006).
const OVERDUE = room('202', 'TechnicalBlock', {
  overdue: true,
  maintenance: { startDate: '2026-10-01', estimatedEndDate: '2026-10-05' },
});
const DISABLED = room('105', 'DisabledForRepairs', { categoryRoom: 'SUITE' });
const IN_REPAIR = room('201', 'DisabledForRepairs', { hasOpenTask: true });
const BLOCKED = room('108', 'TechnicalBlock', {
  maintenance: { startDate: '2026-11-08', estimatedEndDate: '2026-11-11' },
});
const SCHEDULED = room('106', 'Available', {
  maintenance: { startDate: '2026-11-20', estimatedEndDate: '2026-11-22' },
});
const AVAILABLE = room('110', 'Available', { categoryRoom: 'BOUTIQUE' });
const OCCUPIED = room('203', 'Occupied', {
  maintenance: { startDate: '2026-11-25', estimatedEndDate: '2026-11-26' },
});
const PANEL: MaintenancePanel = {
  rooms: [OVERDUE, DISABLED, IN_REPAIR, BLOCKED, SCHEDULED, AVAILABLE, OCCUPIED],
};

function renderPanel() {
  return renderWithProviders(<MaintenancePanelPage />, {
    route: '/mantenimiento',
    path: '/mantenimiento',
    extraRoutes: [
      {
        path: '/mantenimiento/tarea',
        element: (
          <>
            <LocationProbe label="tarea" />
            <Link to="/mantenimiento">Volver al panel</Link>
          </>
        ),
      },
    ],
  });
}

function rowOf(roomNumber: string) {
  return screen.getByText(roomNumber, { selector: 'div' }).closest('tr')!;
}

function buttonsOf(roomNumber: string) {
  return within(rowOf(roomNumber))
    .queryAllByRole('button')
    .map((button) => button.textContent);
}

describe('MaintenancePanelPage', () => {
  beforeEach(() => {
    vi.mocked(getMaintenancePanel).mockReset().mockResolvedValue(PANEL);
    vi.mocked(startRepair).mockReset();
    vi.mocked(reportDamage).mockReset();
    vi.mocked(getLatestDamageReport).mockReset();
    vi.mocked(scheduleTechnicalBlock).mockReset();
    vi.mocked(getCurrentTechnicalBlock).mockReset();
    useSessionStore.setState({
      session: { username: 'jcastro', fullName: 'Jorge Castro', area: 'MANTENIMIENTO' },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('muestra los conteos calculados sobre la lista', async () => {
    renderPanel();

    expect(await screen.findByRole('heading', { name: 'Bienvenido, Jorge Castro' })).toBeVisible();
    expect(screen.getByText('Reparaciones y mantenimientos programados')).toBeVisible();
    await screen.findByText('105', { selector: 'div' });
    const summary = screen.getByRole('region', { name: 'Resumen de habitaciones' });
    expect(
      within(summary).getByText('Inhabilitadas por reparaciones').previousSibling,
    ).toHaveTextContent('2');
    const blocked = within(summary).getByText(/En bloqueo técnico/);
    expect(blocked).toHaveTextContent('En bloqueo técnico · 1 vencido');
    expect(blocked.previousSibling).toHaveTextContent('2');
    // Solo los Scheduled: los de bloqueo técnico no cuentan aunque traigan maintenance.
    expect(
      within(summary).getByText('Con mantenimiento programado').previousSibling,
    ).toHaveTextContent('2');
  });

  it('agrupa las filas y muestra estado, etiquetas y mantenimiento', async () => {
    renderPanel();
    await screen.findByText('105', { selector: 'div' });

    const rows = screen.getAllByRole('row').map((row) => row.textContent ?? '');
    const intervention = rows.findIndex((text) => text === 'Requieren intervención');
    const others = rows.findIndex((text) => text === 'Demás habitaciones');
    expect(intervention).toBeGreaterThan(0);
    expect(others).toBeGreaterThan(intervention);
    expect(rows.findIndex((text) => text.startsWith('202'))).toBeLessThan(others);
    expect(rows.findIndex((text) => text.startsWith('106'))).toBeGreaterThan(others);

    expect(within(rowOf('202')).getByText('Bloqueo técnico (vencido)')).toBeInTheDocument();
    expect(within(rowOf('202')).getByText('01-10-2026 a 05-10-2026')).toBeInTheDocument();
    expect(within(rowOf('201')).getByText('En reparación')).toBeInTheDocument();
    expect(within(rowOf('105')).queryByText('En reparación')).toBeNull();
    expect(within(rowOf('105')).getByText('Suite')).toBeInTheDocument();
    expect(within(rowOf('105')).getByText('Sin programar')).toBeInTheDocument();
    expect(within(rowOf('108')).getByText('Bloqueo técnico')).toBeInTheDocument();
    expect(within(rowOf('106')).getByText('20-11-2026 a 22-11-2026')).toBeInTheDocument();
  });

  it('ofrece las acciones según el estado y el mantenimiento', async () => {
    renderPanel();
    await screen.findByText('105', { selector: 'div' });

    expect(buttonsOf('105')).toEqual([
      'Ver informe',
      'Programar mantenimiento',
      'Iniciar reparaciones',
    ]);
    expect(buttonsOf('201')).toEqual(['Ver informe', 'Programar mantenimiento']);
    expect(buttonsOf('108')).toEqual([
      'Ver informe',
      'Programar mantenimiento',
      'Iniciar reparaciones',
    ]);
    expect(buttonsOf('202')).toEqual([
      'Ver informe',
      'Programar mantenimiento',
      'Iniciar reparaciones',
    ]);
    expect(buttonsOf('106')).toEqual(['Reportar daño', 'Ver informe', 'Programar mantenimiento']);
    expect(buttonsOf('110')).toEqual(['Reportar daño', 'Programar mantenimiento']);
    expect(buttonsOf('203')).toEqual(['Ver informe', 'Programar mantenimiento']);
    // "Ver informe" es del daño en las inhabilitadas y del mantenimiento en las demás.
    expect(
      within(rowOf('105')).getByRole('button', { name: /Ver informe del daño/ }),
    ).toBeInTheDocument();
    expect(
      within(rowOf('108')).getByRole('button', { name: /Ver informe del mantenimiento/ }),
    ).toBeInTheDocument();
  });

  it('busca por número exacto sin títulos de grupo y al vaciar vuelve el listado', async () => {
    renderPanel();
    await screen.findByText('105', { selector: 'div' });
    const search = screen.getByRole('searchbox', { name: 'Buscar habitación por número' });

    await userEvent.type(search, '999');
    expect(screen.getByText('No se encontró la habitación 999')).toBeInTheDocument();

    await userEvent.clear(search);
    await userEvent.type(search, '105');
    expect(screen.queryByText('Requieren intervención')).toBeNull();
    expect(screen.queryByText('110', { selector: 'div' })).toBeNull();

    await userEvent.clear(search);
    expect(screen.getByText('110', { selector: 'div' })).toBeInTheDocument();
    expect(screen.getByText('Requieren intervención')).toBeInTheDocument();
  });

  it('avisa cuando no hay habitaciones para listar', async () => {
    vi.mocked(getMaintenancePanel).mockResolvedValue({ rooms: [] });
    renderPanel();

    expect(await screen.findByText('No hay habitaciones para listar')).toBeInTheDocument();
  });

  it('con una tarea activa lleva a su vista', async () => {
    vi.mocked(getMaintenancePanel).mockResolvedValue({
      activeTask: { taskId: 't1', room: { ...DISABLED, hasOpenTask: true } },
      rooms: [],
    });
    renderPanel();

    expect(await screen.findByTestId('location')).toHaveTextContent('tarea');
  });

  it('vuelve a consultar cada 60 segundos y al volver de una acción', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(startRepair).mockResolvedValue({ taskId: 't1' });
    renderPanel();
    await screen.findByText('105', { selector: 'div' });
    expect(getMaintenancePanel).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(60_000);
    await waitFor(() => expect(getMaintenancePanel).toHaveBeenCalledTimes(2));

    await userEvent.click(
      within(rowOf('105')).getByRole('button', { name: /Iniciar reparaciones/ }),
    );
    await userEvent.click(await screen.findByRole('link', { name: 'Volver al panel' }));
    await screen.findByText('105', { selector: 'div' });
    expect(vi.mocked(getMaintenancePanel).mock.calls.length).toBeGreaterThanOrEqual(4);
  });

  it('"Iniciar reparaciones" muestra "Iniciando…" y lleva a la tarea activa', async () => {
    let resolve: (value: { taskId: string }) => void = () => {};
    vi.mocked(startRepair).mockReturnValue(new Promise((done) => (resolve = done)));
    renderPanel();
    await screen.findByText('105', { selector: 'div' });

    await userEvent.click(
      within(rowOf('105')).getByRole('button', { name: /Iniciar reparaciones/ }),
    );

    expect(
      within(rowOf('105')).getByRole('button', { name: /Iniciar reparaciones/ }),
    ).toHaveTextContent('Iniciando…');
    expect(
      within(rowOf('108')).getByRole('button', { name: /Iniciar reparaciones/ }),
    ).toBeDisabled();
    expect(startRepair).toHaveBeenCalledWith('r105');
    resolve({ taskId: 't1' });

    expect(await screen.findByTestId('location')).toHaveTextContent('tarea');
  });

  it('si se rechaza, dice el estado actual y recarga el panel', async () => {
    vi.mocked(startRepair).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'técnico', {
        currentStatus: 'PendingCleaning',
      }),
    );
    renderPanel();
    await screen.findByText('105', { selector: 'div' });
    vi.mocked(getMaintenancePanel).mockResolvedValue({ rooms: [AVAILABLE] });

    await userEvent.click(
      within(rowOf('105')).getByRole('button', { name: /Iniciar reparaciones/ }),
    );

    expect(
      await screen.findByText('La habitación está Pendiente de limpieza; no necesita reparación.'),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByText('105', { selector: 'div' })).not.toBeInTheDocument(),
    );
  });

  it('muestra el texto de la tabla según el errorCode y el de la red', async () => {
    vi.mocked(startRepair)
      .mockRejectedValueOnce(new ApiRequestError(409, 'ROOM_TASK_EXISTS', 'otro'))
      .mockRejectedValueOnce(new ApiRequestError(0, NETWORK_ERROR, 'red'));
    renderPanel();
    await screen.findByText('105', { selector: 'div' });
    const start = () =>
      userEvent.click(within(rowOf('105')).getByRole('button', { name: /Iniciar reparaciones/ }));

    await start();
    expect(
      await screen.findByText('Otro compañero ya está reparando esta habitación.'),
    ).toBeInTheDocument();

    await start();
    expect(
      await screen.findByText('No hay conexión con el servidor. Revisa tu red e intenta de nuevo.'),
    ).toBeInTheDocument();
  });

  it('"Reportar daño" usa el texto de la vista de mantenimiento y avisa al terminar', async () => {
    vi.mocked(reportDamage).mockResolvedValue({ damageReportId: 'd1' });
    renderPanel();
    await screen.findByText('110', { selector: 'div' });

    await userEvent.click(within(rowOf('110')).getByRole('button', { name: /Reportar daño/ }));
    const dialog = screen.getByRole('alertdialog', { name: 'Reportar daño' });
    expect(dialog).toHaveTextContent(
      'La habitación 110 quedará fuera de servicio hasta que se repare.',
    );
    await userEvent.type(within(dialog).getByLabelText('Descripción del daño *'), 'Grifo roto');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reportar daño' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Daño reportado. La habitación 110 quedó fuera de servicio para reparación.',
    );
  });

  it('"Ver informe" de una inhabilitada muestra el reporte del daño', async () => {
    vi.mocked(getLatestDamageReport).mockResolvedValue({
      roomNumber: '105',
      damageDescription: 'Fuga en el lavamanos.',
      authorName: 'Luis Torres',
      reportDateTime: '2026-11-08T10:05:00-05:00',
    });
    renderPanel();
    await screen.findByText('105', { selector: 'div' });

    await userEvent.click(within(rowOf('105')).getByRole('button', { name: /Ver informe/ }));

    const dialog = await screen.findByRole('dialog', { name: 'Reporte de daño · Habitación 105' });
    expect(await within(dialog).findByText('Fuga en el lavamanos.')).toBeInTheDocument();
    expect(getLatestDamageReport).toHaveBeenCalledWith('r105');
  });

  it('"Programar mantenimiento" avisa con el resultado y recarga el panel', async () => {
    vi.mocked(scheduleTechnicalBlock).mockResolvedValue({
      technicalBlockId: 'b1',
      status: 'Scheduled',
    });
    renderPanel();
    await screen.findByText('110', { selector: 'div' });

    await userEvent.click(
      within(rowOf('110')).getByRole('button', { name: /Programar mantenimiento/ }),
    );
    const dialog = screen.getByRole('alertdialog', { name: 'Programar mantenimiento' });
    expect(dialog).toHaveTextContent('Habitación 110 · Disponible.');
    await userEvent.type(within(dialog).getByLabelText('Motivo del mantenimiento *'), 'Pintura');
    fireEvent.change(within(dialog).getByLabelText('Fecha de inicio *'), {
      target: { value: '2026-11-20' },
    });
    fireEvent.change(within(dialog).getByLabelText('Fecha estimada de fin *'), {
      target: { value: '2026-11-22' },
    });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Programar' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Mantenimiento programado del 20-11-2026 al 22-11-2026 para la habitación 110.',
    );
    expect(scheduleTechnicalBlock).toHaveBeenCalledWith('r110', {
      reason: 'Pintura',
      startDate: '2026-11-20',
      estimatedEndDate: '2026-11-22',
    });
    await waitFor(() => expect(getMaintenancePanel).toHaveBeenCalledTimes(2));
  });

  it('"Ver informe" del mantenimiento muestra su informe', async () => {
    vi.mocked(getCurrentTechnicalBlock).mockResolvedValue({
      roomNumber: '108',
      reason: 'Pintura y resane de paredes.',
      responsibleName: 'Sara Gil',
      startDate: '2026-11-08',
      estimatedEndDate: '2026-11-11',
      reportDateTime: '2026-11-03T14:10:00-05:00',
    });
    renderPanel();
    await screen.findByText('108', { selector: 'div' });

    await userEvent.click(
      within(rowOf('108')).getByRole('button', { name: /Ver informe del mantenimiento/ }),
    );

    const dialog = await screen.findByRole('dialog', { name: 'Mantenimiento · Habitación 108' });
    expect(await within(dialog).findByText('Pintura y resane de paredes.')).toBeInTheDocument();
    expect(within(dialog).getByText('En curso')).toBeInTheDocument();
    expect(getCurrentTechnicalBlock).toHaveBeenCalledWith('r108');
  });
});
