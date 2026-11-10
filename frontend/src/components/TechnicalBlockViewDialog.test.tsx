import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { getCurrentTechnicalBlock } from '@/api/technicalBlockService';
import { TechnicalBlockViewDialog } from '@/components/TechnicalBlockViewDialog';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/technicalBlockService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/technicalBlockService')>()),
  getCurrentTechnicalBlock: vi.fn(),
}));

const BLOCK = {
  roomNumber: '108',
  reason: 'Pintura y resane de paredes.',
  responsibleName: 'Sara Gil',
  startDate: '2026-11-08',
  estimatedEndDate: '2026-11-11',
  reportDateTime: '2026-11-03T14:10:00-05:00',
};

describe('TechnicalBlockViewDialog', () => {
  beforeEach(() => {
    vi.mocked(getCurrentTechnicalBlock).mockReset().mockResolvedValue(BLOCK);
  });

  it('muestra motivo, responsable, fechas, fecha de programación y situación', async () => {
    renderWithProviders(
      <TechnicalBlockViewDialog
        room={{ roomId: 'r108', roomNumber: '108', status: 'TechnicalBlock', overdue: false }}
        onClose={vi.fn()}
      />,
    );

    const dialog = screen.getByRole('dialog', { name: 'Mantenimiento · Habitación 108' });
    expect(await within(dialog).findByText('Pintura y resane de paredes.')).toBeInTheDocument();
    expect(within(dialog).getByText('Sara Gil')).toBeInTheDocument();
    expect(within(dialog).getByText('08-11-2026 a 11-11-2026')).toBeInTheDocument();
    expect(within(dialog).getByText('03-11-2026 14:10')).toBeInTheDocument();
    expect(within(dialog).getByText('En curso')).toBeInTheDocument();
    expect(getCurrentTechnicalBlock).toHaveBeenCalledWith('r108');
  });

  it.each([
    ['TechnicalBlock', true, 'En curso · ya pasó la fecha estimada de fin'],
    ['Occupied', false, 'Programado'],
  ] as const)(
    'la situación de una habitación %s (vencido: %s) es "%s"',
    async (status, overdue, text) => {
      renderWithProviders(
        <TechnicalBlockViewDialog
          room={{ roomId: 'r108', roomNumber: '108', status, overdue }}
          onClose={vi.fn()}
        />,
      );

      expect(await screen.findByText(text)).toBeInTheDocument();
    },
  );

  it('si ya no tiene mantenimiento, lo dice', async () => {
    vi.mocked(getCurrentTechnicalBlock).mockRejectedValue(
      new ApiRequestError(404, 'TECHNICAL_BLOCK_NOT_FOUND', 'no'),
    );
    renderWithProviders(
      <TechnicalBlockViewDialog
        room={{ roomId: 'r108', roomNumber: '108', status: 'Available', overdue: false }}
        onClose={vi.fn()}
      />,
    );

    expect(
      await screen.findByText('Esta habitación no tiene un mantenimiento programado ni en curso.'),
    ).toBeInTheDocument();
  });
});
