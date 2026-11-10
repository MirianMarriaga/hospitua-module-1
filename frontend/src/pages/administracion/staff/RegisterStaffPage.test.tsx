import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { registerStaff } from '@/api/staffService';
import { RegisterStaffPage } from '@/pages/administracion/staff/RegisterStaffPage';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/staffService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/staffService')>()),
  registerStaff: vi.fn(),
}));

interface FormValues {
  username?: string;
  password?: string;
  fullName?: string;
  area?: string;
}

async function fillForm(values: FormValues) {
  if (values.username) await userEvent.type(screen.getByLabelText(/Usuario/), values.username);
  if (values.password) await userEvent.type(screen.getByLabelText(/^Contraseña/), values.password);
  if (values.fullName) {
    await userEvent.type(screen.getByLabelText(/Nombre completo/), values.fullName);
  }
  if (values.area) await chooseOption(screen.getByLabelText(/Área/), values.area);
  await userEvent.click(screen.getByRole('button', { name: 'Registrar personal' }));
}

const VALID = {
  username: ' JRamirez ',
  password: 'Limpieza2026',
  fullName: 'Jorge Ramírez',
  area: 'Limpieza',
};

describe('RegisterStaffPage', () => {
  beforeEach(() => {
    vi.mocked(registerStaff).mockReset();
  });

  it('señala cada campo vacío con su error sin llamar al backend', async () => {
    renderWithProviders(<RegisterStaffPage />);
    await fillForm({});

    expect(await screen.findByText('Ingresa el usuario.')).toBeInTheDocument();
    expect(screen.getByText('Ingresa la contraseña.')).toBeInTheDocument();
    expect(screen.getByText('Ingresa el nombre completo.')).toBeInTheDocument();
    expect(screen.getByText('Selecciona el área.')).toBeInTheDocument();
    expect(registerStaff).not.toHaveBeenCalled();
  });

  it('valida el formato del usuario y la longitud de la contraseña con los textos del plan', async () => {
    renderWithProviders(<RegisterStaffPage />);
    await fillForm({ ...VALID, username: 'josé ramírez', password: 'corta' });

    expect(
      await screen.findByText(
        'El usuario debe tener entre 3 y 30 caracteres: letras sin tilde, números, punto o guion bajo.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument();
    expect(registerStaff).not.toHaveBeenCalled();
  });

  it('una contraseña de solo espacios cuenta como vacía, como en el backend', async () => {
    renderWithProviders(<RegisterStaffPage />);
    await fillForm({ ...VALID, password: '        ' });

    expect(await screen.findByText('Ingresa la contraseña.')).toBeInTheDocument();
    expect(registerStaff).not.toHaveBeenCalled();
  });

  it('la contraseña no queda en la caché de mutaciones al salir de la pantalla', async () => {
    vi.mocked(registerStaff).mockResolvedValue({
      username: 'jramirez',
      fullName: 'Jorge Ramírez',
      area: 'LIMPIEZA',
      active: true,
    });
    const { queryClient, unmount } = renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);
    await screen.findByRole('heading', { name: 'Cuenta de Jorge Ramírez registrada' });

    unmount();
    await waitFor(() => expect(queryClient.getMutationCache().getAll()).toHaveLength(0));
  });

  it('la contraseña admite 72 bytes: una tilde ocupa dos', async () => {
    renderWithProviders(<RegisterStaffPage />);
    await fillForm({ ...VALID, password: 'á'.repeat(37) });

    expect(
      await screen.findByText('La contraseña admite máximo 72 caracteres.'),
    ).toBeInTheDocument();
    expect(registerStaff).not.toHaveBeenCalled();
  });

  it('la contraseña empieza oculta y el ojo la muestra mientras se escribe', async () => {
    renderWithProviders(<RegisterStaffPage />);
    const password = screen.getByLabelText(/^Contraseña/);
    await userEvent.type(password, 'Limpieza2026');
    expect(password).toHaveAttribute('type', 'password');

    await userEvent.click(screen.getByRole('button', { name: 'Ver contraseña' }));
    expect(password).toHaveAttribute('type', 'text');
    expect(password).toHaveValue('Limpieza2026');

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar contraseña' }));
    expect(password).toHaveAttribute('type', 'password');
    expect(registerStaff).not.toHaveBeenCalled();
  });

  it('restringe el área a las 5 opciones', async () => {
    renderWithProviders(<RegisterStaffPage />);
    await userEvent.click(screen.getByLabelText(/Área/));

    const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(options).toEqual([
      'Seleccionar',
      'Recepción',
      'Administración',
      'Gerencia',
      'Limpieza',
      'Mantenimiento',
    ]);
  });

  it('registra con el usuario normalizado y muestra la confirmación sin la contraseña', async () => {
    vi.mocked(registerStaff).mockResolvedValue({
      username: 'jramirez',
      fullName: 'Jorge Ramírez',
      area: 'LIMPIEZA',
      active: true,
    });
    renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);

    expect(
      await screen.findByRole('heading', { name: 'Cuenta de Jorge Ramírez registrada' }),
    ).toBeInTheDocument();
    expect(vi.mocked(registerStaff).mock.calls[0]![0]).toEqual({
      username: 'jramirez',
      password: 'Limpieza2026',
      fullName: 'Jorge Ramírez',
      area: 'LIMPIEZA',
    });
    expect(screen.getByText('Ya puede iniciar sesión con su usuario y contraseña.')).toBeVisible();
    const summary = screen.getByText('Usuario').closest('dl')!;
    expect(within(summary).getByText('jramirez')).toBeInTheDocument();
    expect(within(summary).getByText('Limpieza')).toBeInTheDocument();
    expect(within(summary).getByText('Activa')).toBeInTheDocument();
    expect(screen.queryByText(/Limpieza2026/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al personal' })).toHaveAttribute(
      'href',
      '/administracion/personal',
    );
  });

  it('"Registrar otra persona" vuelve al formulario vacío', async () => {
    vi.mocked(registerStaff).mockResolvedValue({
      username: 'jramirez',
      fullName: 'Jorge Ramírez',
      area: 'LIMPIEZA',
      active: true,
    });
    renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar otra persona' }));

    expect(screen.getByLabelText(/Usuario/)).toHaveValue('');
    expect(screen.getByLabelText(/^Contraseña/)).toHaveValue('');
    expect(screen.getByLabelText(/Nombre completo/)).toHaveValue('');
  });

  it('marca el usuario si ya existe (409 USERNAME_ALREADY_EXISTS)', async () => {
    vi.mocked(registerStaff).mockRejectedValue(
      new ApiRequestError(409, 'USERNAME_ALREADY_EXISTS', 'técnico'),
    );
    renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);

    expect(
      await screen.findByText('Ya existe una cuenta con el usuario jramirez.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Usuario/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('técnico')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('marca el campo que rechaza el backend (details.field)', async () => {
    vi.mocked(registerStaff).mockRejectedValue(
      new ApiRequestError(400, 'VALIDATION_ERROR', 'técnico', { field: 'password' }),
    );
    renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);

    expect(await screen.findByText('Revisa este campo.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Contraseña/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('técnico')).not.toBeInTheDocument();
  });

  it('ante un fallo de red conserva lo escrito y sugiere reintentar', async () => {
    vi.mocked(registerStaff).mockRejectedValue(new ApiRequestError(0, NETWORK_ERROR, 'red'));
    renderWithProviders(<RegisterStaffPage />);
    await fillForm(VALID);

    expect(
      await screen.findByText('No hay conexión con el servidor. Revisa tu red e intenta de nuevo.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Nombre completo/)).toHaveValue('Jorge Ramírez');
  });
});
