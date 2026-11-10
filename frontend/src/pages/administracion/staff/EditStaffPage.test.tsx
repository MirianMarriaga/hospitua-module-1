import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { editStaff, getStaffAccount, type StaffAccountDetail } from '@/api/staffService';
import { EditStaffPage } from '@/pages/administracion/staff/EditStaffPage';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/staffService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/staffService')>()),
  getStaffAccount: vi.fn(),
  editStaff: vi.fn(),
}));

const ACCOUNT: StaffAccountDetail = {
  username: 'jramirez',
  fullName: 'Jorge Ramírez',
  area: 'LIMPIEZA',
  active: true,
  version: 3,
};

function renderPage() {
  return renderWithProviders(<EditStaffPage />, {
    route: '/administracion/personal/jramirez/editar',
    path: '/administracion/personal/:username/editar',
  });
}

async function save() {
  await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
}

describe('EditStaffPage', () => {
  beforeEach(() => {
    vi.mocked(getStaffAccount).mockReset().mockResolvedValue(ACCOUNT);
    vi.mocked(editStaff).mockReset();
  });

  it('precarga el nombre y el área, con el usuario de solo lectura y sin contraseña', async () => {
    renderPage();

    expect(await screen.findByLabelText(/Nombre completo/)).toHaveValue('Jorge Ramírez');
    expect(getStaffAccount).toHaveBeenCalledWith('jramirez');
    const username = screen.getByLabelText('Usuario');
    expect(username).toHaveValue('jramirez');
    expect(username).toHaveAttribute('readonly');
    expect(screen.getByLabelText(/Área/)).toHaveTextContent('Limpieza');
    expect(screen.getByLabelText('Contraseña nueva')).toHaveValue('');
  });

  it('con la contraseña en blanco no la envía y muestra la confirmación de la edición', async () => {
    vi.mocked(editStaff).mockResolvedValue({ ...ACCOUNT, area: 'MANTENIMIENTO', version: 4 });
    renderPage();
    await screen.findByLabelText(/Nombre completo/);

    await chooseOption(screen.getByLabelText(/Área/), 'Mantenimiento');
    await save();

    expect(
      await screen.findByRole('heading', { name: 'Cuenta de Jorge Ramírez actualizada' }),
    ).toBeInTheDocument();
    expect(editStaff).toHaveBeenCalledWith('jramirez', {
      version: 3,
      fullName: 'Jorge Ramírez',
      area: 'MANTENIMIENTO',
    });
    expect(screen.getByText('Los cambios ya están guardados.')).toBeVisible();
    expect(screen.getByText('Mantenimiento')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar otra persona' })).toBeNull();
  });

  it('envía la contraseña nueva cuando se escribe', async () => {
    vi.mocked(editStaff).mockResolvedValue({ ...ACCOUNT, version: 4 });
    renderPage();
    await screen.findByLabelText(/Nombre completo/);

    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'Limpieza2027');
    await save();

    await waitFor(() =>
      expect(editStaff).toHaveBeenCalledWith('jramirez', {
        version: 3,
        fullName: 'Jorge Ramírez',
        area: 'LIMPIEZA',
        password: 'Limpieza2027',
      }),
    );
  });

  it('valida el nombre vacío y la contraseña corta sin llamar al backend', async () => {
    renderPage();
    await userEvent.clear(await screen.findByLabelText(/Nombre completo/));
    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'corta');
    await save();

    expect(await screen.findByText('Ingresa el nombre completo.')).toBeInTheDocument();
    expect(screen.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument();
    expect(editStaff).not.toHaveBeenCalled();
  });

  it('ante una edición simultánea avisa y recarga los datos actuales', async () => {
    vi.mocked(editStaff).mockRejectedValue(new ApiRequestError(409, 'CONCURRENT_UPDATE', 'x'));
    renderPage();
    const name = await screen.findByLabelText(/Nombre completo/);
    await userEvent.clear(name);
    await userEvent.type(name, 'Jorge R.');
    await save();

    expect(
      await screen.findByText(/La información de la cuenta ya fue actualizada por otro usuario/),
    ).toBeInTheDocument();

    vi.mocked(getStaffAccount).mockResolvedValue({
      ...ACCOUNT,
      fullName: 'Jorge Ramírez Díaz',
      version: 4,
    });
    await userEvent.click(screen.getByRole('button', { name: 'Cargar los datos actuales' }));

    await waitFor(() =>
      expect(screen.getByLabelText(/Nombre completo/)).toHaveValue('Jorge Ramírez Díaz'),
    );
    expect(screen.queryByText(/ya fue actualizada por otro usuario/)).not.toBeInTheDocument();
  });

  it('si es el último administrador, lo dice en el área', async () => {
    vi.mocked(getStaffAccount).mockResolvedValue({ ...ACCOUNT, area: 'ADMINISTRACION' });
    vi.mocked(editStaff).mockRejectedValue(new ApiRequestError(409, 'LAST_ADMINISTRATOR', 'x'));
    renderPage();
    await screen.findByLabelText(/Nombre completo/);
    await chooseOption(screen.getByLabelText(/Área/), 'Gerencia');
    await save();

    expect(
      await screen.findByText(
        'No se puede cambiar el área: es la única cuenta activa de Administración.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Área/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('una cuenta desactivada no se puede editar', async () => {
    vi.mocked(getStaffAccount).mockResolvedValue({ ...ACCOUNT, active: false });
    renderPage();

    expect(await screen.findByText('No se puede editar la cuenta jramirez')).toBeInTheDocument();
    expect(screen.getByText('La cuenta está desactivada; no se puede editar.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al personal' })).toHaveAttribute(
      'href',
      '/administracion/personal',
    );
  });

  it('si la desactivan mientras se edita, muestra el texto de la tabla', async () => {
    vi.mocked(editStaff).mockRejectedValue(new ApiRequestError(409, 'ACCOUNT_INACTIVE', 'x'));
    renderPage();
    await screen.findByLabelText(/Nombre completo/);
    await save();

    expect(
      await screen.findByText('La cuenta está desactivada; no se puede editar.'),
    ).toBeInTheDocument();
  });

  it('si la cuenta no existe, lo dice y ofrece volver', async () => {
    vi.mocked(getStaffAccount).mockRejectedValue(new ApiRequestError(404, 'STAFF_NOT_FOUND', 'x'));
    renderPage();

    expect(await screen.findByText('Cuenta no encontrada')).toBeInTheDocument();
    expect(screen.getByText('No existe ninguna cuenta con ese usuario.')).toBeVisible();
  });
});
