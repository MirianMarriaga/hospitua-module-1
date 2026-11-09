import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import {
  completeCleaning,
  getCleaningPanel,
  releaseCleaning,
  type CleaningPanel,
} from '@/api/cleaningService';
import { ActiveCleaningTaskPage } from '@/pages/limpieza/ActiveCleaningTaskPage';
import type { CleaningPanelState } from '@/pages/limpieza/cleaningRoutes';
import { useSessionStore } from '@/stores/sessionStore';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/cleaningService', () => ({
  getCleaningPanel: vi.fn(),
  completeCleaning: vi.fn(),
  releaseCleaning: vi.fn(),
}));

const WITH_TASK: CleaningPanel = {
  activeTask: {
    taskId: 't-1',
    startDateTime: '2026-11-08T09:05:00-05:00',
    room: { roomId: 'r101', roomNumber: '101', categoryRoom: 'DOBLE', status: 'InCleaning' },
  },
  rooms: [],
};
const WITHOUT_TASK: CleaningPanel = { rooms: [] };

function PanelProbe() {
  const state = useLocation().state as CleaningPanelState | null;
  return <div data-testid="panel">{state?.notice ?? 'sin aviso'}</div>;
}

function renderTask() {
  return renderWithProviders(<ActiveCleaningTaskPage />, {
    route: '/limpieza/tarea',
    path: '/limpieza/tarea',
    extraRoutes: [{ path: '/limpieza', element: <PanelProbe /> }],
  });
}

async function openConfirmEnd() {
  await userEvent.click(await screen.findByRole('button', { name: 'Confirmar fin' }));
  return screen.findByRole('alertdialog', {
    name: '¿Está seguro que desea finalizar la limpieza de la habitación 101?',
  });
}

describe('ActiveCleaningTaskPage', () => {
  beforeEach(() => {
    vi.mocked(getCleaningPanel).mockReset().mockResolvedValue(WITH_TASK);
    vi.mocked(completeCleaning).mockReset();
    vi.mocked(releaseCleaning).mockReset();
    useSessionStore.setState({
      session: { username: 'ltorres', fullName: 'Luis Torres', area: 'LIMPIEZA' },
    });
  });

  it('muestra la habitación en limpieza y solo "Confirmar fin" y "Liberar tarea"', async () => {
    renderTask();

    const card = await screen.findByRole('region', { name: 'Habitación en limpieza' });
    expect(within(card).getByText('101')).toBeInTheDocument();
    expect(within(card).getByText('Doble')).toBeInTheDocument();
    expect(within(card).getByText('En limpieza')).toBeInTheDocument();
    expect(within(card).getByText('08-11-2026 09:05')).toBeInTheDocument();
    expect(within(card).getByText(/^\d+ min$/)).toBeInTheDocument();
    expect(within(card).getByText('Luis Torres')).toBeInTheDocument();
    expect(
      within(card)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['Liberar tarea', 'Confirmar fin']);
  });

  it('si el panel no trae tarea activa, vuelve al panel', async () => {
    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);
    renderTask();

    expect(await screen.findByTestId('panel')).toHaveTextContent('sin aviso');
  });

  it('cancelar la confirmación no envía nada', async () => {
    renderTask();
    const dialog = await openConfirmEnd();

    expect(
      within(dialog).getByText('La habitación quedará lista para recibir huéspedes.'),
    ).toBeVisible();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(completeCleaning).not.toHaveBeenCalled();
  });

  it('confirma sin daño y vuelve al panel con el aviso', async () => {
    vi.mocked(completeCleaning).mockResolvedValue(undefined);
    renderTask();
    const dialog = await openConfirmEnd();

    expect(within(dialog).queryByText(/Como describiste un daño/)).not.toBeInTheDocument();
    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(completeCleaning).toHaveBeenCalledWith('t-1', undefined);
    // Solo lo seguro: sin el estado final no dice si quedó disponible, reservada o bloqueada.
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      /^Limpieza de la habitación 101 finalizada\.$/,
    );
  });

  it('con daño descrito advierte la inhabilitación y lo envía', async () => {
    vi.mocked(completeCleaning).mockResolvedValue(undefined);
    renderTask();
    const dialog = await openConfirmEnd();

    const field = within(dialog).getByLabelText('Describir daño encontrado (opcional)');
    expect(field).toHaveAttribute('placeholder', 'Déjalo vacío si no encontraste daños.');
    await userEvent.type(field, '  Grifo con fuga  ');
    expect(within(dialog).getByText('14 / 500')).toBeInTheDocument();
    expect(within(dialog).getByText(/Como describiste un daño/)).toBeVisible();

    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(completeCleaning).toHaveBeenCalledWith('t-1', 'Grifo con fuga');
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      'Limpieza de la habitación 101 finalizada con daño reportado.',
    );
  });

  it('no deja confirmar con más de 500 caracteres', async () => {
    renderTask();
    const dialog = await openConfirmEnd();

    const field = within(dialog).getByLabelText('Describir daño encontrado (opcional)');
    await userEvent.click(field);
    await userEvent.paste('a'.repeat(501));

    expect(within(dialog).getByText('501 / 500')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Confirmar fin' })).toBeDisabled();
  });

  it('muestra el error del backend dentro del diálogo y conserva la descripción', async () => {
    vi.mocked(completeCleaning).mockRejectedValue(
      new ApiRequestError(409, 'TASK_ALREADY_CLOSED', 'cerrada'),
    );
    renderTask();
    const dialog = await openConfirmEnd();
    const field = within(dialog).getByLabelText('Describir daño encontrado (opcional)');
    await userEvent.type(field, 'Espejo roto');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(await within(dialog).findByText('Esta tarea ya fue cerrada.')).toBeInTheDocument();
    expect(field).toHaveValue('Espejo roto');
  });

  it('si la tarea ya se cerró, el diálogo conserva el error y al cerrarlo vuelve al panel', async () => {
    vi.mocked(completeCleaning).mockRejectedValue(
      new ApiRequestError(409, 'TASK_ALREADY_CLOSED', 'cerrada'),
    );
    renderTask();
    const dialog = await openConfirmEnd();
    // Tras el rechazo, el panel recargado ya no trae la tarea (FR-016)
    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));
    await waitFor(() => expect(getCleaningPanel).toHaveBeenCalledTimes(2));
    expect(await within(dialog).findByText('Esta tarea ya fue cerrada.')).toBeVisible();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByTestId('panel')).toHaveTextContent('sin aviso');
  });

  it('una descripción con solo espacios no es un daño (FR-018)', async () => {
    vi.mocked(completeCleaning).mockResolvedValue(undefined);
    renderTask();
    const dialog = await openConfirmEnd();

    await userEvent.type(
      within(dialog).getByLabelText('Describir daño encontrado (opcional)'),
      '   ',
    );
    expect(within(dialog).queryByText(/Como describiste un daño/)).not.toBeInTheDocument();
    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar fin' }));

    expect(completeCleaning).toHaveBeenCalledWith('t-1', undefined);
    // Solo lo seguro: sin el estado final no dice si quedó disponible, reservada o bloqueada.
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      /^Limpieza de la habitación 101 finalizada\.$/,
    );
  });

  it('libera la tarea y vuelve al panel con el aviso', async () => {
    vi.mocked(releaseCleaning).mockResolvedValue(undefined);
    renderTask();

    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog', {
      name: '¿Está seguro que desea liberar la limpieza de la habitación 101?',
    });
    expect(
      within(dialog).getByText(
        'La habitación volverá a la lista como pendiente de limpieza y otro compañero podrá limpiarla.',
      ),
    ).toBeVisible();
    vi.mocked(getCleaningPanel).mockResolvedValue(WITHOUT_TASK);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Liberar tarea' }));

    expect(releaseCleaning).toHaveBeenCalledWith('t-1');
    expect(await screen.findByTestId('panel')).toHaveTextContent(
      'Dejaste la habitación 101 para otro compañero. Vuelve a aparecer como pendiente de limpieza.',
    );
  });

  it('cancelar la liberación no envía nada', async () => {
    renderTask();
    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(releaseCleaning).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Liberar tarea' })).toBeVisible();
  });

  it('al liberar muestra el texto de la tabla según el errorCode y el de la red', async () => {
    vi.mocked(releaseCleaning).mockRejectedValueOnce(
      new ApiRequestError(403, 'TASK_NOT_OWNED', 'otro'),
    );
    renderTask();
    await userEvent.click(await screen.findByRole('button', { name: 'Liberar tarea' }));
    const dialog = await screen.findByRole('alertdialog');
    const confirm = within(dialog).getByRole('button', { name: 'Liberar tarea' });

    await userEvent.click(confirm);
    expect(
      await within(dialog).findByText('Esta habitación la está limpiando otro compañero.'),
    ).toBeInTheDocument();

    vi.mocked(releaseCleaning).mockRejectedValueOnce(new ApiRequestError(0, NETWORK_ERROR, 'red'));
    await userEvent.click(confirm);
    expect(
      await within(dialog).findByText(
        'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.',
      ),
    ).toBeInTheDocument();
  });
});
