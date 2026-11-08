import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getInventory, type Room } from '@/api/roomService';
import type { PagedResult } from '@/api/types';
import { InventoryPage } from '@/pages/administracion/inventory/InventoryPage';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/roomService', () => ({ getInventory: vi.fn() }));

const ROOMS: Room[] = [
  {
    roomId: 'a',
    roomNumber: '101',
    floor: 1,
    categoryRoom: 'SENCILLA',
    maxCapacity: 1,
    baseRate: 180000,
    status: 'Available',
    version: 1,
  },
  {
    roomId: 'b',
    roomNumber: '204',
    floor: 2,
    categoryRoom: 'BOUTIQUE',
    maxCapacity: 2,
    baseRate: 380000,
    status: 'Inactive',
    version: 1,
  },
  {
    roomId: 'c',
    roomNumber: '305',
    floor: 3,
    categoryRoom: 'SUITE',
    maxCapacity: 4,
    baseRate: 520000,
    status: 'TechnicalBlock',
    version: 1,
  },
];

function page(items: Room[], meta: Partial<PagedResult<Room>> = {}): PagedResult<Room> {
  return {
    items,
    page: 1,
    size: 10,
    totalElements: items.length,
    totalPages: 1,
    ...meta,
  };
}

function rowOf(number: string) {
  return screen.getByRole('cell', { name: number }).closest('tr')!;
}

describe('InventoryPage', () => {
  beforeEach(() => {
    vi.mocked(getInventory).mockReset();
    vi.mocked(getInventory).mockResolvedValue(page(ROOMS, { totalElements: 23, totalPages: 3 }));
  });

  it('muestra las habitaciones en español, sin el UUID, con su paginación', async () => {
    renderWithProviders(<InventoryPage role="admin" />);

    expect(await screen.findByText('Mostrando 1–10 de 23')).toBeInTheDocument();
    const row = rowOf('204');
    expect(within(row).getByText('Boutique')).toBeInTheDocument();
    expect(within(row).getByText('Inactiva')).toBeInTheDocument();
    expect(within(row).getByText('Piso 2')).toBeInTheDocument();
    expect(screen.queryByText('b')).not.toBeInTheDocument();
  });

  it('el Administrador ve las acciones habilitadas con enlaces según el estado', async () => {
    renderWithProviders(<InventoryPage role="admin" />);
    await screen.findByText('Mostrando 1–10 de 23');

    const editLink = within(rowOf('101')).getByRole('link', { name: 'Editar' });
    expect(editLink).toHaveAttribute('href', '/administracion/habitaciones/a/editar');

    const decommissionLink = within(rowOf('101')).getByRole('link', { name: 'Dar de baja' });
    expect(decommissionLink).toHaveAttribute('href', '/administracion/habitaciones/a/baja');

    const reactivateLink = within(rowOf('204')).getByRole('link', {
      name: 'Marcar como disponible',
    });
    expect(reactivateLink).toHaveAttribute('href', '/administracion/habitaciones/b/reactivacion');

    expect(within(rowOf('305')).queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Registrar habitación/ })).toBeInTheDocument();
  });

  it('el Gerente ve "Ver historial" con el roomId de la habitación', async () => {
    renderWithProviders(<InventoryPage role="manager" />);
    await screen.findByText('Mostrando 1–10 de 23');

    const link = within(rowOf('305')).getByRole('link', { name: 'Ver historial' });
    expect(link).toHaveAttribute('href', '/gerencia/historial?roomId=c&roomNumber=305');
  });

  it('pasa los filtros y la página de la URL a la consulta', async () => {
    renderWithProviders(<InventoryPage role="admin" />, {
      route: '/?categoryRoom=SUITE&status=Occupied&floor=3&sort=baseRate-desc&page=2',
    });
    await screen.findByText('Mostrando 1–10 de 23');

    expect(getInventory).toHaveBeenCalledWith(
      { categoryRoom: 'SUITE', status: 'Occupied', floor: 3, sort: 'baseRate', direction: 'desc' },
      2,
    );
  });

  it('al cambiar un filtro vuelve a la página 1 y consulta con él', async () => {
    renderWithProviders(<InventoryPage role="admin" />, { route: '/?page=3' });
    await screen.findByText('Mostrando 1–10 de 23');

    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'DOBLE');

    await waitFor(() =>
      expect(getInventory).toHaveBeenLastCalledWith({ categoryRoom: 'DOBLE' }, 1),
    );
  });

  it('el número se aplica al dejar de escribir', async () => {
    renderWithProviders(<InventoryPage role="admin" />);
    await screen.findByText('Mostrando 1–10 de 23');

    await userEvent.type(screen.getByLabelText('Número'), '204');

    await waitFor(() => expect(getInventory).toHaveBeenLastCalledWith({ roomNumber: '204' }, 1));
    expect(getInventory).not.toHaveBeenCalledWith({ roomNumber: '2' }, 1);
  });

  it('distingue el inventario vacío de los filtros sin resultados', async () => {
    vi.mocked(getInventory).mockResolvedValue(page([]));
    const { unmount } = renderWithProviders(<InventoryPage role="admin" />);
    expect(await screen.findByText('Todavía no hay habitaciones registradas.')).toBeInTheDocument();
    unmount();

    renderWithProviders(<InventoryPage role="admin" />, { route: '/?status=Reserved' });
    expect(
      await screen.findByText(/Ninguna habitación cumple los filtros seleccionados/),
    ).toBeInTheDocument();
  });
});
