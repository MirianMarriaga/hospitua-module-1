import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { deactivateStaff, listStaff, type StaffAccount } from '@/api/staffService';
import type { PagedResult } from '@/api/types';
import { StaffListPage } from '@/pages/administracion/staff/StaffListPage';
import { chooseOption } from '@/test/chooseOption';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/staffService', () => ({ listStaff: vi.fn(), deactivateStaff: vi.fn() }));

const ACCOUNTS: StaffAccount[] = [
  { username: 'jramirez', fullName: 'Jorge Ramírez', area: 'LIMPIEZA', active: true },
  { username: 'lmendez', fullName: 'Laura Méndez', area: 'ADMINISTRACION', active: true },
  { username: 'ovargas', fullName: 'Óscar Vargas', area: 'MANTENIMIENTO', active: false },
];

function page(
  items: StaffAccount[],
  meta: Partial<PagedResult<StaffAccount>> = {},
): PagedResult<StaffAccount> {
  return { items, page: 1, size: 10, totalElements: items.length, totalPages: 1, ...meta };
}

function rowOf(username: string) {
  return screen.getByRole('cell', { name: username }).closest('tr')!;
}

describe('StaffListPage', () => {
  beforeEach(() => {
    vi.mocked(listStaff).mockReset();
    vi.mocked(deactivateStaff).mockReset();
    vi.mocked(listStaff).mockResolvedValue(page(ACCOUNTS, { totalElements: 12, totalPages: 2 }));
  });

  it('muestra usuario, nombre, área y estado en español, con su paginación', async () => {
    renderWithProviders(<StaffListPage />);

    expect(await screen.findByText('Mostrando 1–10 de 12')).toBeInTheDocument();
    for (const column of ['Usuario', 'Nombre', 'Área', 'Estado']) {
      expect(screen.getByRole('columnheader', { name: column })).toBeInTheDocument();
    }
    const active = rowOf('jramirez');
    expect(within(active).getByText('Jorge Ramírez')).toBeInTheDocument();
    expect(within(active).getByText('Limpieza')).toBeInTheDocument();
    expect(within(active).getByText('Activa')).toBeInTheDocument();
    const inactive = rowOf('ovargas');
    expect(within(inactive).getByText('Mantenimiento')).toBeInTheDocument();
    expect(within(inactive).getByText('Desactivada')).toBeInTheDocument();
    expect(screen.queryByText('LIMPIEZA')).not.toBeInTheDocument();
  });

  it('"Editar" y "Desactivar" solo aparecen en las cuentas activas', async () => {
    renderWithProviders(<StaffListPage />);
    await screen.findByText('Mostrando 1–10 de 12');

    const active = rowOf('jramirez');
    expect(within(active).getByRole('link', { name: 'Editar' })).toHaveAttribute(
      'href',
      '/administracion/personal/jramirez/editar',
    );
    expect(within(active).getByRole('button', { name: 'Desactivar' })).toBeInTheDocument();
    const inactive = rowOf('ovargas');
    expect(within(inactive).queryByRole('link')).not.toBeInTheDocument();
    expect(within(inactive).queryByRole('button')).not.toBeInTheDocument();
  });

  it('"Desactivar" abre la confirmación de esa cuenta y, al confirmar, vuelve a consultar la lista', async () => {
    vi.mocked(deactivateStaff).mockResolvedValue({ ...ACCOUNTS[0]!, active: false });
    renderWithProviders(<StaffListPage />);
    await screen.findByText('Mostrando 1–10 de 12');

    await userEvent.click(within(rowOf('jramirez')).getByRole('button', { name: 'Desactivar' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText('¿Desactivar la cuenta de Jorge Ramírez?')).toBeVisible();
    const calls = vi.mocked(listStaff).mock.calls.length;

    await userEvent.click(within(dialog).getByRole('button', { name: 'Desactivar cuenta' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(deactivateStaff).toHaveBeenCalledWith('jramirez');
    await waitFor(() => expect(vi.mocked(listStaff).mock.calls.length).toBeGreaterThan(calls));
  });

  it('"Registrar personal" está sobre la lista', async () => {
    renderWithProviders(<StaffListPage />);
    await screen.findByText('Mostrando 1–10 de 12');

    expect(screen.getByRole('link', { name: /Registrar personal/ })).toHaveAttribute(
      'href',
      '/administracion/personal/nuevo',
    );
  });

  it('pasa los filtros y la página de la URL a la consulta', async () => {
    renderWithProviders(<StaffListPage />, {
      route: '/?query=ram&area=LIMPIEZA&active=false&page=2',
    });
    await screen.findByText('Mostrando 1–10 de 12');

    expect(listStaff).toHaveBeenCalledWith({ query: 'ram', area: 'LIMPIEZA', active: false }, 2);
  });

  it('ignora los filtros de la URL que el backend rechazaría', async () => {
    renderWithProviders(<StaffListPage />, { route: '/?area=COCINA&active=quiza&page=0' });
    await screen.findByText('Mostrando 1–10 de 12');

    expect(listStaff).toHaveBeenCalledWith({}, 1);
  });

  it('al cambiar el área o el estado vuelve a la página 1 y consulta con el filtro', async () => {
    renderWithProviders(<StaffListPage />, { route: '/?page=2' });
    await screen.findByText('Mostrando 1–10 de 12');

    await chooseOption(screen.getByLabelText('Área'), 'Recepción');
    await waitFor(() => expect(listStaff).toHaveBeenLastCalledWith({ area: 'RECEPCION' }, 1));

    await chooseOption(screen.getByLabelText('Estado'), 'Desactivada');
    await waitFor(() =>
      expect(listStaff).toHaveBeenLastCalledWith({ area: 'RECEPCION', active: false }, 1),
    );
  });

  it('la búsqueda se aplica al dejar de escribir', async () => {
    renderWithProviders(<StaffListPage />);
    await screen.findByText('Mostrando 1–10 de 12');

    await userEvent.type(screen.getByLabelText('Buscar'), 'ram');

    await waitFor(() => expect(listStaff).toHaveBeenLastCalledWith({ query: 'ram' }, 1));
    expect(listStaff).not.toHaveBeenCalledWith({ query: 'r' }, 1);
  });

  it('distingue la lista vacía de los filtros sin resultados', async () => {
    vi.mocked(listStaff).mockResolvedValue(page([]));
    const { unmount } = renderWithProviders(<StaffListPage />);
    expect(await screen.findByText('Todavía no hay cuentas registradas.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).not.toBeInTheDocument();
    unmount();

    renderWithProviders(<StaffListPage />, { route: '/?area=GERENCIA' });
    expect(await screen.findByText(/Ninguna cuenta cumple los filtros aplicados/)).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    await waitFor(() => expect(listStaff).toHaveBeenLastCalledWith({}, 1));
  });

  it('muestra el error de la consulta', async () => {
    vi.mocked(listStaff).mockRejectedValue(new ApiRequestError(400, 'VALIDATION_ERROR', 'x'));
    renderWithProviders(<StaffListPage />);

    expect(await screen.findByText('Revisa los filtros aplicados.')).toBeInTheDocument();
  });
});
