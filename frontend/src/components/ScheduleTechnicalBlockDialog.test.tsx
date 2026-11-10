import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, NETWORK_ERROR } from '@/api/apiClient';
import { scheduleTechnicalBlock } from '@/api/technicalBlockService';
import type { RoomStatus } from '@/api/types';
import { ScheduleTechnicalBlockDialog } from '@/components/ScheduleTechnicalBlockDialog';
import { renderWithProviders } from '@/test/renderWithProviders';

vi.mock('@/api/technicalBlockService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/technicalBlockService')>()),
  scheduleTechnicalBlock: vi.fn(),
}));
// "Hoy" fijo en Bogotá para las reglas de fechas.
vi.mock('@/lib/format', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/format')>()),
  todayInBogota: () => '2026-11-09',
}));

function renderDialog(status: RoomStatus = 'Available') {
  const onScheduled = vi.fn();
  const onClose = vi.fn();
  renderWithProviders(
    <ScheduleTechnicalBlockDialog
      room={{ roomId: 'r106', roomNumber: '106', status }}
      refreshKey={['maintenance']}
      onClose={onClose}
      onScheduled={onScheduled}
    />,
  );
  const dialog = screen.getByRole('alertdialog', { name: 'Programar mantenimiento' });
  return { dialog, onScheduled, onClose };
}

async function fill(dialog: HTMLElement, reason: string, start: string, end: string) {
  if (reason) {
    await userEvent.type(within(dialog).getByLabelText('Motivo del mantenimiento *'), reason);
  }
  fireEvent.change(within(dialog).getByLabelText('Fecha de inicio *'), {
    target: { value: start },
  });
  fireEvent.change(within(dialog).getByLabelText('Fecha estimada de fin *'), {
    target: { value: end },
  });
}

function submit(dialog: HTMLElement, name = 'Programar') {
  return userEvent.click(within(dialog).getByRole('button', { name }));
}

describe('ScheduleTechnicalBlockDialog', () => {
  beforeEach(() => {
    vi.mocked(scheduleTechnicalBlock).mockReset();
  });

  it('muestra los campos obligatorios con el texto de la vista', () => {
    const { dialog } = renderDialog('Occupied');

    expect(dialog).toHaveTextContent(
      'Habitación 106 · Ocupada. Desde ahora no se podrán reservar esas fechas.',
    );
    expect(within(dialog).getByLabelText('Motivo del mantenimiento *')).toHaveAttribute(
      'placeholder',
      'Describe el mantenimiento preventivo que se realizará.',
    );
    expect(within(dialog).getByText('0 / 500')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Fecha de inicio *')).toHaveAttribute('type', 'date');
    expect(
      within(dialog).getByText('Si inicia hoy, la habitación debe estar disponible.'),
    ).toBeVisible();
    expect(within(dialog).getByText('Máximo 90 días.')).toBeVisible();
  });

  it.each([
    [
      '',
      '2026-11-20',
      '2026-11-22',
      'Escribe el motivo del mantenimiento (máximo 500 caracteres).',
    ],
    ['Pintura', '', '2026-11-22', 'Elige una fecha válida (DD-MM-YYYY).'],
    [
      'Pintura',
      '2026-11-22',
      '2026-11-20',
      'La fecha de fin no puede ser anterior a la fecha de inicio.',
    ],
    ['Pintura', '2026-11-08', '2026-11-10', 'La fecha de inicio no puede ser anterior a hoy.'],
    ['Pintura', '2026-11-10', '2027-02-09', 'El mantenimiento no puede durar más de 90 días.'],
    [
      'Pintura',
      '2027-11-10',
      '2027-11-11',
      'Solo se puede programar con hasta 365 días de anticipación.',
    ],
  ])(
    'valida antes de enviar y dice la regla incumplida (%s, %s, %s)',
    async (reason, start, end, message) => {
      const { dialog } = renderDialog();
      await fill(dialog, reason, start, end);

      await submit(dialog);

      expect(within(dialog).getByText(message)).toBeInTheDocument();
      expect(scheduleTechnicalBlock).not.toHaveBeenCalled();
    },
  );

  it('acepta justo 90 días de rango y 365 de anticipación', async () => {
    vi.mocked(scheduleTechnicalBlock).mockResolvedValue({
      technicalBlockId: 'b1',
      status: 'Scheduled',
    });
    const { dialog, onScheduled } = renderDialog();
    await fill(dialog, 'Pintura', '2027-11-09', '2028-02-07');

    await submit(dialog);

    expect(scheduleTechnicalBlock).toHaveBeenCalled();
    expect(onScheduled).toHaveBeenCalled();
  });

  it('si inicia hoy, la habitación debe estar disponible', async () => {
    const { dialog } = renderDialog('Occupied');
    await fill(dialog, 'Pintura', '2026-11-09', '2026-11-10');

    await submit(dialog);

    expect(
      within(dialog).getByText(
        'Hoy la habitación está Ocupada. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior.',
      ),
    ).toBeInTheDocument();
    expect(scheduleTechnicalBlock).not.toHaveBeenCalled();
  });

  it('programa a futuro, envía el motivo sin espacios y avisa con las fechas', async () => {
    vi.mocked(scheduleTechnicalBlock).mockResolvedValue({
      technicalBlockId: 'b1',
      status: 'Scheduled',
    });
    const { dialog, onScheduled, onClose } = renderDialog('Occupied');
    await fill(dialog, '  Cambio de alfombra  ', '2026-11-20', '2026-11-22');

    await submit(dialog);

    expect(scheduleTechnicalBlock).toHaveBeenCalledWith('r106', {
      reason: 'Cambio de alfombra',
      startDate: '2026-11-20',
      estimatedEndDate: '2026-11-22',
    });
    expect(onClose).toHaveBeenCalled();
    expect(onScheduled).toHaveBeenCalledWith(
      'Mantenimiento programado del 20-11-2026 al 22-11-2026 para la habitación 106.',
    );
  });

  it('si inicia hoy y queda aplicado, avisa que la habitación queda fuera de servicio', async () => {
    vi.mocked(scheduleTechnicalBlock).mockResolvedValue({
      technicalBlockId: 'b1',
      status: 'Applied',
    });
    const { dialog, onScheduled } = renderDialog('Available');
    await fill(dialog, 'Pintura', '2026-11-09', '2026-11-12');

    await submit(dialog);

    expect(onScheduled).toHaveBeenCalledWith(
      'Mantenimiento iniciado. La habitación 106 queda fuera de servicio hasta el 12-11-2026 (fecha estimada).',
    );
  });

  it('con reservas en esas fechas muestra el aviso y la lista de reservas', async () => {
    vi.mocked(scheduleTechnicalBlock).mockRejectedValue(
      new ApiRequestError(409, 'RESERVATION_CONFLICT', 'reservas', {
        reservations: [
          {
            reservationRef: 'RES-10502',
            startDate: '2026-11-21',
            endDate: '2026-11-24',
            status: 'ACTIVE',
          },
        ],
      }),
    );
    const { dialog, onScheduled } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-20', '2026-11-22');

    await submit(dialog);

    expect(
      await within(dialog).findByText(
        'La habitación tiene reservas en esas fechas. Habla con Reservas o elige otras fechas.',
      ),
    ).toBeInTheDocument();
    const row = within(dialog).getByRole('cell', { name: 'RES-10502' }).closest('tr')!;
    expect(row).toHaveTextContent('21-11-2026');
    expect(row).toHaveTextContent('24-11-2026');
    expect(onScheduled).not.toHaveBeenCalled();
  });

  it('con otro mantenimiento en esas fechas dice cuál', async () => {
    vi.mocked(scheduleTechnicalBlock).mockRejectedValue(
      new ApiRequestError(409, 'MAINTENANCE_CONFLICT', 'cruce', {
        conflicts: [{ maintenanceStart: '2026-11-21', maintenanceEnd: '2026-11-23' }],
      }),
    );
    const { dialog } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-20', '2026-11-22');

    await submit(dialog);

    expect(
      await within(dialog).findByText(
        'Ya hay un mantenimiento programado o en curso del 21-11-2026 al 23-11-2026. Elige otras fechas.',
      ),
    ).toBeInTheDocument();
  });

  it('si Módulo 2 no responde, ofrece "Reintentar" y reenvía lo mismo', async () => {
    vi.mocked(scheduleTechnicalBlock)
      .mockRejectedValueOnce(new ApiRequestError(424, 'MODULE_2_UNAVAILABLE', 'm2'))
      .mockResolvedValueOnce({ technicalBlockId: 'b1', status: 'Scheduled' });
    const { dialog, onScheduled } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-20', '2026-11-22');

    await submit(dialog);
    expect(
      await within(dialog).findByText(
        'Servicio de reservas temporalmente no disponible. No se programó el mantenimiento; inténtalo de nuevo.',
      ),
    ).toBeInTheDocument();

    await submit(dialog, 'Reintentar');

    expect(scheduleTechnicalBlock).toHaveBeenCalledTimes(2);
    expect(vi.mocked(scheduleTechnicalBlock).mock.calls[1]).toEqual(
      vi.mocked(scheduleTechnicalBlock).mock.calls[0],
    );
    expect(onScheduled).toHaveBeenCalled();
  });

  it.each([
    [
      new ApiRequestError(400, 'VALIDATION_ERROR', 'v', { field: 'reason' }),
      'Escribe el motivo del mantenimiento (máximo 500 caracteres).',
    ],
    [
      new ApiRequestError(400, 'VALIDATION_ERROR', 'v', { field: 'startDate' }),
      'Elige una fecha válida (DD-MM-YYYY).',
    ],
    [
      new ApiRequestError(400, 'INVALID_DATE_RANGE', 'r', { rule: 'RANGE_TOO_LONG' }),
      'El mantenimiento no puede durar más de 90 días.',
    ],
    [
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 's', { currentStatus: 'Inactive' }),
      'La habitación está dada de baja; no se puede programar mantenimiento.',
    ],
    [
      new ApiRequestError(409, 'ROOM_INVALID_STATE', 's', { currentStatus: 'InCleaning' }),
      'Hoy la habitación está En limpieza. Para empezar hoy debe estar disponible; elige una fecha de inicio posterior.',
    ],
    [new ApiRequestError(404, 'ROOM_NOT_FOUND', 'n'), 'No se encontró la habitación.'],
    [
      new ApiRequestError(0, NETWORK_ERROR, 'red'),
      'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.',
    ],
  ])('muestra el texto del rechazo del backend (%s)', async (error, message) => {
    vi.mocked(scheduleTechnicalBlock).mockRejectedValue(error);
    const { dialog } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-20', '2026-11-22');

    await submit(dialog);

    expect(await within(dialog).findByText(message)).toBeInTheDocument();
  });

  it('al cambiar un dato se quita el error anterior', async () => {
    const { dialog } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-22', '2026-11-20');
    await submit(dialog);
    const message = 'La fecha de fin no puede ser anterior a la fecha de inicio.';
    expect(within(dialog).getByText(message)).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Fecha estimada de fin *'), {
      target: { value: '2026-11-25' },
    });

    expect(within(dialog).queryByText(message)).not.toBeInTheDocument();
  });

  it('cancelar no envía nada', async () => {
    const { dialog, onClose } = renderDialog();
    await fill(dialog, 'Pintura', '2026-11-20', '2026-11-22');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(onClose).toHaveBeenCalled();
    expect(scheduleTechnicalBlock).not.toHaveBeenCalled();
  });
});
