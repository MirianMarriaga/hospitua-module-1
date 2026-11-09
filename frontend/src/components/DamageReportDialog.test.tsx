import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { reportDamage } from '@/api/damageReportService';
import { DamageReportDialog } from '@/components/DamageReportDialog';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/damageReportService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/damageReportService')>()),
  reportDamage: vi.fn(),
}));

const ROOM = { roomId: 'r102', roomNumber: '102' };

function renderDialog() {
  const onClose = vi.fn();
  const onReported = vi.fn();
  const view = renderWithProviders(
    <DamageReportDialog
      room={ROOM}
      description="La habitación 102 quedará fuera de servicio hasta que mantenimiento la repare."
      refreshKey={['panel']}
      onClose={onClose}
      onReported={onReported}
    />,
  );
  const invalidate = vi.spyOn(view.queryClient, 'invalidateQueries');
  return { onClose, onReported, invalidate };
}

const dialog = () => screen.getByRole('alertdialog', { name: 'Reportar daño' });
const confirmButton = () => within(dialog()).getByRole('button', { name: 'Reportar daño' });
const field = () => screen.getByLabelText('Descripción del daño *');

describe('DamageReportDialog', () => {
  beforeEach(() => {
    vi.mocked(reportDamage).mockReset();
  });

  it('muestra el texto del panel y no deja confirmar sin descripción', async () => {
    renderDialog();

    expect(dialog()).toHaveTextContent(
      'La habitación 102 quedará fuera de servicio hasta que mantenimiento la repare.',
    );
    expect(screen.getByText('0 / 500')).toBeInTheDocument();
    expect(confirmButton()).toBeDisabled();

    await userEvent.type(field(), '   ');
    expect(confirmButton()).toBeDisabled();
  });

  it('cuenta hasta 500 caracteres y por encima avisa y no deja confirmar', async () => {
    renderDialog();

    await userEvent.type(field(), 'Fuga en el lavamanos');
    expect(screen.getByText('20 / 500')).toBeInTheDocument();
    expect(confirmButton()).toBeEnabled();

    await userEvent.clear(field());
    await userEvent.click(field());
    await userEvent.paste('x'.repeat(501));
    expect(screen.getByText('501 / 500')).toBeInTheDocument();
    expect(
      screen.getByText('La descripción del daño es obligatoria (máximo 500 caracteres).'),
    ).toBeInTheDocument();
    expect(field()).toHaveAttribute('aria-invalid', 'true');
    expect(confirmButton()).toBeDisabled();
  });

  it('envía la descripción sin espacios en los extremos y avisa al panel', async () => {
    vi.mocked(reportDamage).mockResolvedValue({ damageReportId: 'd1' });
    const { onClose, onReported, invalidate } = renderDialog();

    await userEvent.type(field(), '  Ventana con el vidrio fisurado.  ');
    await userEvent.click(confirmButton());

    await waitFor(() => expect(onReported).toHaveBeenCalledWith(ROOM));
    expect(reportDamage).toHaveBeenCalledWith('r102', 'Ventana con el vidrio fisurado.');
    expect(onClose).toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['panel'] });
  });

  it('si la habitación cambió de estado lo dice, conserva el texto y recarga el panel', async () => {
    vi.mocked(reportDamage).mockRejectedValue(
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 'técnico', { currentStatus: 'Occupied' }),
    );
    const { onReported, invalidate } = renderDialog();

    await userEvent.type(field(), 'Ventana rota');
    await userEvent.click(confirmButton());

    expect(
      await screen.findByText(
        'La habitación está Ocupada; ahora no se puede reportar un daño en ella.',
      ),
    ).toBeInTheDocument();
    expect(field()).toHaveValue('Ventana rota');
    expect(onReported).not.toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['panel'] });
  });

  it('ante un fallo de red conserva el texto y deja intentar de nuevo', async () => {
    vi.mocked(reportDamage).mockRejectedValue(
      new ApiRequestError(0, NETWORK_ERROR, 'No hay conexión con el servidor.'),
    );
    renderDialog();

    await userEvent.type(field(), 'Ventana rota');
    await userEvent.click(confirmButton());

    expect(
      await screen.findByText('No hay conexión con el servidor. Revisa tu red e intenta de nuevo.'),
    ).toBeInTheDocument();
    expect(field()).toHaveValue('Ventana rota');
    expect(confirmButton()).toBeEnabled();
  });

  it('cancelar no envía nada', async () => {
    const { onClose } = renderDialog();

    await userEvent.type(field(), 'Ventana rota');
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onClose).toHaveBeenCalled();
    expect(reportDamage).not.toHaveBeenCalled();
  });
});
