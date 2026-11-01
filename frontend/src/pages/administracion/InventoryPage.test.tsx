import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getInventory } from '@/api/roomService';
import { InventoryPage } from '@/pages/administracion/InventoryPage';
import { page, room } from '@/test/fixtures/rooms';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/roomService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/roomService')>()),
  getInventory: vi.fn(),
}));

const ROOMS = [
  room({ roomId: 'a', roomNumber: '101', status: 'Available' }),
  room({ roomId: 'b', roomNumber: '204', status: 'Inactive', categoryRoom: 'BOUTIQUE', floor: 2 }),
  room({ roomId: 'c', roomNumber: '305', status: 'Occupied', categoryRoom: 'SUITE', floor: 3 }),
];

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

  it('el Administrador ve las acciones por estado, deshabilitadas hasta la Fase 5', async () => {
    renderWithProviders(<InventoryPage role="admin" />);
    await screen.findByText('Mostrando 1–10 de 23');

    expect(within(rowOf('101')).getByRole('button', { name: 'Editar' })).toBeDisabled();
    expect(within(rowOf('101')).getByRole('button', { name: 'Dar de baja' })).toBeDisabled();
    expect(
      within(rowOf('204')).getByRole('button', { name: 'Marcar como disponible' }),
    ).toBeDisabled();
    expect(within(rowOf('305')).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Registrar habitación/ })).toBeInTheDocument();
  });

  it('el Gerente ve "Ver historial" con el roomId de la habitación', async () => {
    renderWithProviders(<InventoryPage role="manager" />);
    await screen.findByText('Mostrando 1–10 de 23');

    const link = within(rowOf('305')).getByRole('link', { name: 'Ver historial' });
    expect(link).toHaveAttribute('href', '/gerencia/historial?roomId=c&roomNumber=305');
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
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
    expect(await screen.findByText('No hay habitaciones registradas')).toBeInTheDocument();
    unmount();

    renderWithProviders(<InventoryPage role="admin" />, { route: '/?status=Reserved' });
    expect(
      await screen.findByText(/Ninguna habitación cumple los filtros aplicados/),
    ).toBeInTheDocument();
  });
});
