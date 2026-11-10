import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { deactivateStaff, type StaffAccount } from '@/api/staffService';
import { DeactivateStaffDialog } from '@/pages/administracion/staff/DeactivateStaffDialog';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/staffService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/staffService')>()),
  deactivateStaff: vi.fn(),
}));

const ACCOUNT: StaffAccount = {
  username: 'fgomez',
  fullName: 'Felipe Gómez',
  area: 'LIMPIEZA',
  active: true,
};

const onClose = vi.fn();

function renderDialog() {
  renderWithProviders(<DeactivateStaffDialog account={ACCOUNT} onClose={onClose} />);
  return screen.getByRole('alertdialog');
}

function renderDialogWithClient() {
  const { queryClient } = renderWithProviders(
    <DeactivateStaffDialog account={ACCOUNT} onClose={onClose} />,
  );
  return { dialog: screen.getByRole('alertdialog'), queryClient };
}

async function confirm(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole('button', { name: 'Desactivar cuenta' }));
}

describe('DeactivateStaffDialog', () => {
  beforeEach(() => {
    vi.mocked(deactivateStaff).mockReset();
    onClose.mockReset();
  });

  it('muestra usuario, nombre y área, y advierte que no podrá iniciar sesión', () => {
    const dialog = renderDialog();

    expect(within(dialog).getByText('¿Desactivar la cuenta de Felipe Gómez?')).toBeVisible();
    const summary = within(dialog).getByText('Usuario').closest('dl')!;
    expect(within(summary).getByText('fgomez')).toBeInTheDocument();
    expect(within(summary).getByText('Felipe Gómez')).toBeInTheDocument();
    expect(within(summary).getByText('Limpieza')).toBeInTheDocument();
    expect(within(dialog).getByText('no podrá iniciar sesión')).toBeVisible();
    expect(within(dialog).getByText(/La cuenta no se borra/)).toBeVisible();
    expect(deactivateStaff).not.toHaveBeenCalled();
  });

  it('cancelar no envía nada', async () => {
    const dialog = renderDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(onClose).toHaveBeenCalled();
    expect(deactivateStaff).not.toHaveBeenCalled();
  });

  it('al confirmar desactiva la cuenta y cierra el diálogo', async () => {
    vi.mocked(deactivateStaff).mockResolvedValue({ ...ACCOUNT, active: false });
    const dialog = renderDialog();
    await confirm(dialog);

    await vi.waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(deactivateStaff).toHaveBeenCalledWith('fgomez');
  });

  it.each([
    ['CANNOT_DEACTIVATE_SELF', 'No puedes desactivar tu propia cuenta.'],
    ['LAST_ADMINISTRATOR', 'No se puede desactivar: es la única cuenta activa de Administración.'],
    ['ACCOUNT_INACTIVE', 'La cuenta ya está desactivada.'],
    ['STAFF_NOT_FOUND', 'No se encontró la cuenta.'],
  ])('ante %s muestra su texto y solo deja cerrar', async (code, text) => {
    vi.mocked(deactivateStaff).mockRejectedValue(new ApiRequestError(409, code, 'técnico'));
    const dialog = renderDialog();
    await confirm(dialog);

    expect(await within(dialog).findByText(text)).toBeInTheDocument();
    expect(within(dialog).queryByText('técnico')).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Desactivar cuenta' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cerrar' })).toBeEnabled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('si otro Administrador ya la desactivó, vuelve a consultar la lista', async () => {
    vi.mocked(deactivateStaff).mockRejectedValue(
      new ApiRequestError(409, 'ACCOUNT_INACTIVE', 'técnico'),
    );
    const { dialog, queryClient } = renderDialogWithClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    await confirm(dialog);

    await within(dialog).findByText('La cuenta ya está desactivada.');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['staff'] });
  });

  it('ante un fallo de red deja reintentar', async () => {
    vi.mocked(deactivateStaff).mockRejectedValue(new ApiRequestError(0, NETWORK_ERROR, 'red'));
    const dialog = renderDialog();
    await confirm(dialog);

    expect(
      await within(dialog).findByText(
        'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.',
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Desactivar cuenta' })).toBeEnabled();
  });
});
