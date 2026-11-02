import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { AreaLayout } from '@/layouts/AreaLayout';
import { useSessionStore } from '@/stores/sessionStore';
import { LocationProbe, renderWithProviders } from '@/test/renderWithProviders';

function renderGuarded(route: string) {
  return renderWithProviders(<AreaLayout area="RECEPCION" />, {
    route,
    path: '/',
    extraRoutes: [
      { path: '/login', element: <LocationProbe label="login" /> },
      { path: '/gerencia/inventario', element: <LocationProbe label="gerencia" /> },
    ],
  });
}

describe('AreaLayout', () => {
  beforeEach(() => {
    useSessionStore.setState({ session: null, expired: false });
  });

  it('sin sesión lleva al login', () => {
    renderGuarded('/');
    expect(screen.getByTestId('location')).toHaveTextContent('login');
  });

  it('con sesión de otra área lleva a la pantalla de inicio de la suya', () => {
    useSessionStore.setState({
      session: { username: 'g', fullName: 'Andrés Rojas', area: 'GERENCIA' },
    });
    renderGuarded('/');
    expect(screen.getByTestId('location')).toHaveTextContent('gerencia');
  });

  it('muestra el usuario y el área, y cierra la sesión', async () => {
    useSessionStore.setState({
      session: { username: 'ana', fullName: 'Ana Ramírez', area: 'RECEPCION' },
    });
    renderGuarded('/');

    expect(screen.getByText('Ana Ramírez')).toBeInTheDocument();
    expect(screen.getByText('Recepcionista')).toBeInTheDocument();
    expect(screen.getByText('AR')).toBeInTheDocument();
    // Recepción no tiene barra lateral
    expect(screen.queryByRole('link', { name: 'Panel de recepción' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Salir/ }));
    expect(useSessionStore.getState().session).toBeNull();
    expect(screen.getByTestId('location')).toHaveTextContent('login');
  });

  it('para administración sí muestra el menú del Sidebar', () => {
    useSessionStore.setState({
      session: { username: 'admin', fullName: 'Laura Méndez', area: 'ADMINISTRACION' },
    });
    renderWithProviders(<AreaLayout area="ADMINISTRACION" />, { route: '/' });
    expect(screen.getByRole('link', { name: 'Inventario' })).toBeInTheDocument();
  });
});
