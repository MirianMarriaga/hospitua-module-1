import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { login } from '@/api/authService';
import { LoginPage } from '@/pages/login/LoginPage';
import { useSessionStore } from '@/stores/sessionStore';
import { LocationProbe, renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/authService', () => ({ login: vi.fn() }));

function renderLogin() {
  return renderWithProviders(<LoginPage />, {
    route: '/login',
    path: '/login',
    extraRoutes: [
      { path: '/administracion/inventario', element: <LocationProbe label="inventario" /> },
    ],
  });
}

async function fillAndSubmit(username: string, password: string) {
  await userEvent.type(screen.getByLabelText('Usuario'), username);
  await userEvent.type(screen.getByLabelText('Contraseña'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(login).mockReset();
    useSessionStore.setState({ session: null, expired: false });
  });

  it('pide usuario y contraseña antes de llamar al backend', async () => {
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Ingresa tu usuario.')).toBeInTheDocument();
    expect(screen.getByText('Ingresa tu contraseña.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('muestra el texto de INVALID_CREDENTIALS en el formulario', async () => {
    vi.mocked(login).mockRejectedValue(
      new ApiRequestError(401, 'INVALID_CREDENTIALS', 'Credenciales inválidas'),
    );
    renderLogin();
    await fillAndSubmit('admin', 'mala');

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument();
    expect(screen.queryByText('Credenciales inválidas')).not.toBeInTheDocument();
  });

  it('guarda la sesión y lleva al inicio de su área', async () => {
    vi.mocked(login).mockResolvedValue({
      username: 'admin',
      fullName: 'Administrador',
      area: 'ADMINISTRACION',
    });
    renderLogin();
    await fillAndSubmit('admin', 'admin123');

    expect(await screen.findByTestId('location')).toHaveTextContent('inventario');
    expect(login).toHaveBeenCalledWith(
      { username: 'admin', password: 'admin123' },
      expect.anything(),
    );
    expect(useSessionStore.getState().session?.area).toBe('ADMINISTRACION');
  });

  it('después del login vuelve a la pantalla que se pidió sin sesión', async () => {
    vi.mocked(login).mockResolvedValue({
      username: 'recepcion',
      fullName: 'Ana Ramírez',
      area: 'RECEPCION',
    });
    renderWithProviders(<LoginPage />, {
      route: { pathname: '/login', state: { from: '/check-out?reservationRef=RES-1&roomId=r1' } },
      path: '/login',
      extraRoutes: [{ path: '/check-out', element: <LocationProbe label="check-out" /> }],
    });
    await fillAndSubmit('recepcion', 'admin123');

    expect(await screen.findByTestId('location')).toHaveTextContent('check-out');
  });

  it('avisa que la sesión terminó', () => {
    useSessionStore.setState({ session: null, expired: true });
    renderLogin();
    expect(screen.getByText('Tu sesión terminó. Inicia sesión de nuevo.')).toBeInTheDocument();
  });
});
