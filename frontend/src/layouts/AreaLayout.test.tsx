import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { AreaLayout } from '@/layouts/AreaLayout';
import { useSessionStore } from '@/stores/sessionStore';
import { useThemeStore } from '@/stores/themeStore';
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
    // Recepción no tiene menú del área, como en su vista
    expect(screen.queryByRole('link', { name: 'Panel de recepción' })).not.toBeInTheDocument();

    // "Salir" está en el menú del usuario
    await userEvent.click(screen.getByRole('button', { name: 'Menú de Ana Ramírez' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Salir/ }));
    expect(useSessionStore.getState().session).toBeNull();
    expect(screen.getByTestId('location')).toHaveTextContent('login');
  });

  it('para administración sí muestra el menú del Sidebar', () => {
    useSessionStore.setState({
      session: { username: 'admin', fullName: 'Laura Méndez', area: 'ADMINISTRACION' },
    });
    renderWithProviders(<AreaLayout area="ADMINISTRACION" />, { route: '/' });
    expect(screen.getByRole('link', { name: 'Inventario' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Personal' })).toHaveAttribute(
      'href',
      '/administracion/personal',
    );
    // Como en la vista 01: "Registrar habitación" es un botón del inventario, no una opción del menú
    expect(screen.queryByRole('link', { name: 'Registrar habitación' })).not.toBeInTheDocument();
  });

  it('pliega y despliega la barra lateral sin perder los nombres del menú', async () => {
    const user = userEvent.setup();
    useSessionStore.setState({
      session: { username: 'admin', fullName: 'Laura Méndez', area: 'ADMINISTRACION' },
    });
    renderWithProviders(<AreaLayout area="ADMINISTRACION" />, { route: '/' });
    const toggle = screen.getByRole('button', { name: 'Mostrar u ocultar la barra lateral' });
    expect(toggle).toHaveAttribute('aria-expanded', 'true');

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    const inventario = screen.getByRole('link', { name: 'Inventario' });
    expect(inventario).toHaveAttribute('title', 'Inventario');
    expect(within(inventario).getByText('Inventario')).toHaveClass('min-[901px]:sr-only');

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(within(inventario).getByText('Inventario')).not.toHaveClass('min-[901px]:sr-only');
  });

  it('muestra la ruta con el área y la opción del menú', () => {
    useSessionStore.setState({
      session: { username: 'admin', fullName: 'Laura Méndez', area: 'ADMINISTRACION' },
    });
    renderWithProviders(<AreaLayout area="ADMINISTRACION" />, {
      route: '/administracion/inventario',
      path: '/administracion/inventario',
    });
    const ruta = screen.getByRole('navigation', { name: 'Ruta' });
    expect(ruta).toHaveTextContent('Administración');
    expect(within(ruta).getByText('Inventario')).toHaveAttribute('aria-current', 'page');
  });

  it('cambia entre el tema claro y el oscuro desde el menú del usuario', async () => {
    useThemeStore.setState({ theme: 'light' });
    useSessionStore.setState({
      session: { username: 'ana', fullName: 'Ana Ramírez', area: 'RECEPCION' },
    });
    renderGuarded('/');

    await userEvent.click(screen.getByRole('button', { name: 'Menú de Ana Ramírez' }));
    await userEvent.click(await screen.findByRole('menuitemradio', { name: 'Oscuro' }));
    expect(useThemeStore.getState().theme).toBe('dark');
    expect(screen.getByRole('menuitemradio', { name: 'Oscuro' })).toHaveAttribute(
      'aria-checked',
      'true',
    );

    await userEvent.click(screen.getByRole('menuitemradio', { name: 'Claro' }));
    expect(useThemeStore.getState().theme).toBe('light');
  });
});
