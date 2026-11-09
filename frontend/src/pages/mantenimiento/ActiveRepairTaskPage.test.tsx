import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { getLatestDamageReport } from '@/api/damageReportService';
import {
  completeRepair,
  getMaintenancePanel,
  releaseRepair,
  type MaintenancePanel,
  type MaintenanceRoom,
} from '@/api/maintenanceService';
import { getCurrentTechnicalBlock } from '@/api/technicalBlockService';
import { ActiveRepairTaskPage } from '@/pages/mantenimiento/ActiveRepairTaskPage';
import type { MaintenancePanelState } from '@/pages/mantenimiento/maintenanceRoutes';
import { useSessionStore } from '@/stores/sessionStore';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/maintenanceService', () => ({
  getMaintenancePanel: vi.fn(),
  completeRepair: vi.fn(),
  releaseRepair: vi.fn(),
}));
vi.mock('@/api/damageReportService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/damageReportService')>()),
  getLatestDamageReport: vi.fn(),
}));
vi.mock('@/api/technicalBlockService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/technicalBlockService')>()),
  getCurrentTechnicalBlock: vi.fn(),
}));

const DISABLED: MaintenanceRoom = {
  roomId: 'r105',
  roomNumber: '105',
  categoryRoom: 'SUITE',
  status: 'DisabledForRepairs',
  overdue: false,
  hasOpenTask: true,
};
const BLOCKED: MaintenanceRoom = {
  roomId: 'r202',
  roomNumber: '202',
  categoryRoom: 'DOBLE',
  status: 'TechnicalBlock',
  overdue: true,
  hasOpenTask: true,
  maintenance: { startDate: '2026-11-01', estimatedEndDate: '2026-11-05' },
};
const withTask = (room: MaintenanceRoom): MaintenancePanel => ({
  activeTask: { taskId: 't-1', room },
  rooms: [],
});
const WITHOUT_TASK: MaintenancePanel = { rooms: [] };

function PanelProbe() {
  const state = useLocation().state as MaintenancePanelState | null;
  return <div data-testid="panel">{state?.notice ?? 'sin aviso'}</div>;
}

function renderTask() {
  return renderWithProviders(<ActiveRepairTaskPage />, {
    route: '/mantenimiento/tarea',
    path: '/mantenimiento/tarea',
    extraRoutes: [{ path: '/mantenimiento', element: <PanelProbe /> }],
  });
}

async function openConfirmEnd(roomNumber = '105') {
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar fin' }));
  return screen.findByRole('alertdialog', {
    name: `¿Está seguro que desea finalizar las reparaciones de la habitación ${roomNumber}?`,
  });
}

describe('ActiveRepairTaskPage', () => {
  beforeEach(() => {
    vi.mocked(getMaintenancePanel).mockReset().mockResolvedValue(withTask(DISABLED));
    vi.mocked(completeRepair).mockReset();
    vi.mocked(releaseRepair).mockReset();
    vi.mocked(getLatestDamageReport).mockReset().mockResolvedValue({
      roomNumber: '105',
      damageDescription: 'Fuga en el lavamanos.',
      authorName: 'Luis Torres',
      reportDateTime: '2026-11-08T10:05:00-05:00',
    });
    vi.mocked(getCurrentTechnicalBlock).mockReset().mockResolvedValue({
      roomNumber: '202',
      reason: 'Cambio del aire acondicionado.',
      responsibleName: 'Sara Gil',
      startDate: '2026-11-01',
      estimatedEndDate: '2026-11-05',
      reportDateTime: '2026-10-28T09:00:00-05:00',
    });
    useSessionStore.setState({
      session: { username: 'jcastro', fullName: 'Jorge Castro', area: 'MANTENIMIENTO' },
    });
  });

  it('muestra la habitación, el reporte del daño y solo "Confirmar fin" y "Liberar tarea"', async () => {
    renderTask();

    const card = await screen.findByRole('region', { name: 'Habitación en reparación' });
    expect(within(card).getByText('105')).toBeInTheDocument();
    expect(within(card).getByText('Suite')).toBeInTheDocument();
    expect(within(card).getByText('Inhabilitada por reparaciones')).toBeInTheDocument();
    expect(within(card).getByText('Jorge Castro')).toBeInTheDocument();

    const report = screen.getByRole('region', { name: 'Reporte de daño' });
    expect(await within(report).findByText('Fuga en el lavamanos.')).toBeInTheDocument();
    expect(within(report).getByText('Luis Torres')).toBeInTheDocument();
    expect(within(report).getByText('08-11-2026 10:05')).toBeInTheDocument();
    expect(getLatestDamageReport).toHaveBeenCalledWith('r105');

    expect(screen.getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Liberar tarea',
      'Confirmar fin',
    ]);
  });

  it('en bloqueo técnico muestra el estado vencido y el mantenimiento programado', async () => {
    vi.mocked(getMaintenancePanel).mockResolvedValue(withTask(BLOCKED));
    renderTask();

    const card = await screen.findByRole('region', { name: 'Habitación en reparación' });
    expect(within(card).getByText('Bloqueo técnico (vencido)')).toBeInTheDocument();
    const report = screen.getByRole('region', { name: 'Mantenimiento programado' });
    expect(await within(report).findByText('Cambio del aire acondicionado.')).toBeInTheDocument();
    expect(within(report).getByText('Sara Gil')).toBeInTheDocument();
    expect(within(report).getByText('01-11-2026 a 05-11-2026')).toBeInTheDocument();
    expect(getLatestDamageReport).not.toHaveBeenCalled();
  });

  it('si el panel no trae tarea activa, vuelve al panel', async () => {
    vi.mocked(getMaintenancePanel).mockResolvedValue(WITHOUT_TASK);
    renderTask();

    expect(await screen.findByTestId('panel')).toHaveTextContent('sin aviso');
  });

  it('el diálogo advierte la cola de limpieza y cancelar no envía nada', async () => {
    renderTask();
    const dialog = await openConfirmEnd();

    expect(
      within(dialog).getByText(
        'La habitación pasará a limpieza antes de volver a recibir huéspedes.',
      ),
    ).toBeVisible();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(completeRepair).not.toHaveBeenCalled();
  });

  it('confirma el fin y vuelve al panel con el aviso', async () => {
    vi.mocked(completeRepair).mockResolvedValue(undefined);
    renderTask();
    const dialog = await openConfirmEnd();
    vi.mocked(getMaintenancePanel).mockResolvedValue(WITHOUT_TASK);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(completeRepair).toHaveBeenCalledWith('t-1');
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      'Reparación de la habitación 105 finalizada. La habitación pasó a limpieza antes de volver a recibir huéspedes.',
    );
  });

  it('desde un bloqueo técnico, el aviso dice que el mantenimiento quedó terminado', async () => {
    vi.mocked(getMaintenancePanel).mockResolvedValue(withTask(BLOCKED));
    vi.mocked(completeRepair).mockResolvedValue(undefined);
    renderTask();
    const dialog = await openConfirmEnd('202');
    vi.mocked(getMaintenancePanel).mockResolvedValue(WITHOUT_TASK);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(await screen.findByTestId('panel')).toHaveTextContent(
      'Reparación de la habitación 202 finalizada. La habitación pasó a limpieza y el mantenimiento quedó terminado.',
    );
  });

  it('si la tarea ya se cerró, el diálogo conserva el error y al cerrarlo vuelve al panel', async () => {
    vi.mocked(completeRepair).mockRejectedValue(
      new ApiRequestError(409, 'TASK_ALREADY_CLOSED', 'cerrada'),
    );
    renderTask();
    const dialog = await openConfirmEnd();
    // Tras el rechazo, el panel recargado ya no trae la tarea (FR-018).
    vi.mocked(getMaintenancePanel).mockResolvedValue(WITHOUT_TASK);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));
    await waitFor(() => expect(getMaintenancePanel).toHaveBeenCalledTimes(2));
    expect(await within(dialog).findByText('Esta tarea ya fue cerrada.')).toBeVisible();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByTestId('panel')).toHaveTextContent('sin aviso');
  });

  it('cancelar la liberación no envía nada', async () => {
    renderTask();
    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(releaseRepair).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Liberar tarea' })).toBeVisible();
  });

  it('si no se puede leer lo que hay que reparar, lo dice sin ocultar las acciones', async () => {
    vi.mocked(getLatestDamageReport).mockRejectedValue(
      new ApiRequestError(404, 'DAMAGE_REPORT_NOT_FOUND', 'sin reporte'),
    );
    renderTask();

    expect(
      await screen.findByText('Esta habitación no tiene un daño reportado.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar fin' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Liberar tarea' })).toBeVisible();
  });

  it('libera la tarea y vuelve al panel con el aviso', async () => {
    vi.mocked(releaseRepair).mockResolvedValue(undefined);
    renderTask();

    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog', {
      name: '¿Está seguro que desea liberar la reparación de la habitación 105?',
    });
    expect(
      within(dialog).getByText(
        'La habitación seguirá fuera de servicio y otro compañero podrá continuar la reparación.',
      ),
    ).toBeVisible();
    vi.mocked(getMaintenancePanel).mockResolvedValue(WITHOUT_TASK);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Liberar tarea' }));

    expect(releaseRepair).toHaveBeenCalledWith('t-1');
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      'Dejaste la habitación 105 para otro compañero.',
    );
  });

  it('muestra el texto de la tabla según el errorCode y el de la red', async () => {
    vi.mocked(releaseRepair)
      .mockRejectedValueOnce(new ApiRequestError(403, 'TASK_NOT_OWNED', 'otro'))
      .mockRejectedValueOnce(new ApiRequestError(0, NETWORK_ERROR, 'red'));
    renderTask();
    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog');
    const confirm = within(dialog).getByRole('button', { name: 'Liberar tarea' });

    await userEvent.click(confirm);
    expect(
      await within(dialog).findByText('Esta reparación la está haciendo otro compañero.'),
    ).toBeInTheDocument();

    await userEvent.click(confirm);
    expect(
      await within(dialog).findByText(
        'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.',
      ),
    ).toBeInTheDocument();
  });

  it('al confirmar muestra el texto de TASK_NOT_FOUND', async () => {
    vi.mocked(completeRepair).mockRejectedValue(new ApiRequestError(404, 'TASK_NOT_FOUND', 'no'));
    renderTask();
    const dialog = await openConfirmEnd();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(
      await within(dialog).findByText('No encontramos esta reparación. Vuelve al panel.'),
    ).toBeInTheDocument();
  });
});
