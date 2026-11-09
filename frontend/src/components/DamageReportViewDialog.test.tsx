import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError } from '@/api/apiClient';
import { getLatestDamageReport } from '@/api/damageReportService';
import { DamageReportViewDialog } from '@/components/DamageReportViewDialog';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/damageReportService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/damageReportService')>()),
  getLatestDamageReport: vi.fn(),
}));

describe('DamageReportViewDialog', () => {
  beforeEach(() => {
    vi.mocked(getLatestDamageReport).mockReset();
  });

  it('muestra qué se dañó, quién lo reportó y cuándo', async () => {
    vi.mocked(getLatestDamageReport).mockResolvedValue({
      roomNumber: '101',
      damageDescription: 'Ventana con el vidrio fisurado.',
      authorName: 'Ana Gómez',
      reportDateTime: '2026-10-08T11:20:00-05:00',
    });
    const onClose = vi.fn();
    renderWithProviders(
      <DamageReportViewDialog room={{ roomId: 'r101', roomNumber: '101' }} onClose={onClose} />,
    );

    expect(
      screen.getByRole('dialog', { name: 'Reporte de daño · Habitación 101' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('Ventana con el vidrio fisurado.')).toBeInTheDocument();
    expect(screen.getByText('Ana Gómez')).toBeInTheDocument();
    expect(screen.getByText('08-10-2026 11:20')).toBeInTheDocument();
    expect(getLatestDamageReport).toHaveBeenCalledWith('r101');

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('si la habitación ya no tiene un daño reportado lo dice', async () => {
    vi.mocked(getLatestDamageReport).mockRejectedValue(
      new ApiRequestError(404, 'DAMAGE_REPORT_NOT_FOUND', 'técnico'),
    );
    renderWithProviders(
      <DamageReportViewDialog room={{ roomId: 'r101', roomNumber: '101' }} onClose={vi.fn()} />,
    );

    expect(
      await screen.findByText('Esta habitación no tiene un daño reportado.'),
    ).toBeInTheDocument();
  });
});
